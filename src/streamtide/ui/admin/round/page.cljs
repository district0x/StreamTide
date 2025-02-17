(ns streamtide.ui.admin.round.page
  "Page to manage a specific round "
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
    [cljs-web3-next.helpers :refer [zero-address]]
    [clojure.string :as str]
    [district.ui.component.form.input :refer [amount-input pending-button text-input]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.subs :as router-subs]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [reagent.ratom :refer [reaction]]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.shared.utils :as shared-utils :refer [to-base-amount]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.admin.round.events :as r-events]
    [streamtide.ui.admin.round.subs :as r-subs]
    [streamtide.ui.components.custom-select :refer [select]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo social-links]]
    [streamtide.ui.config :refer [config-map]]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 1000)

(def multiplier-factors [1 0.66 0.33 0])

(defn build-round-info-query [{:keys [:round/id]}]
  [:round
   {:round/id id}
   [:round/start
    :round/duration
    [:round/matching-pools [[:matching-pool/coin [:coin/symbol
                                                  :coin/decimals
                                                  :coin/address
                                                  :coin/chain-id]]
                            :matching-pool/amount
                            :matching-pool/distributed]]]])

(defn build-round-is-last-query [{:keys [:round/id]}]
  [:search-rounds
   {:first 1
    :after (str (dec (int id)))
    :order-by :rounds.order-by/id
    :order-dir :asc}
   [:has-next-page]])

(def donations-order [{:key "amount/desc" :value "Granted Higher"}
                      {:key "amount/asc" :value "Granted Lower"}
                      {:key "date/desc" :value "Newest"}
                      {:key "date/asc" :value "Oldest"}
                      {:key "username/asc" :value "Artist Name"}])

(def other-coin-value "OTHER")

;; TODO get existing coins from server
(def matching-pool-coin [{:value "ETH" :label "ETH"}
                         {:value other-coin-value :label "Other"}])

(def matching-pool-chain (map (fn [chain] {:value (:chain-id chain) :label (:chain-name chain)})
                              (cons (-> config-map :web3-chain) (-> config-map :multichain-matching-pool))))

(defn build-donations-query [{:keys [:round :order-key]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-donations
     (cond-> {:first page-size
              :round round}
             after                   (assoc :after after)
             order-by                (assoc :order-by (keyword "donations.order-by" order-by))
             order-dir               (assoc :order-dir (keyword order-dir)))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:donation/id
               :donation/date
               :donation/amount
               [:donation/coin [:coin/symbol
                                :coin/decimals]]
               [:donation/receiver [:user/address
                                    :user/name
                                    :user/photo
                                    :user/blacklisted
                                    [:user/socials [:social/network
                                                    :social/url
                                                    :social/verified]]]]
               [:donation/sender [:user/address
                                  :user/name
                                  :user/blacklisted
                                  [:user/socials [:social/network
                                                  :social/url
                                                  :social/verified]]]]]]]]))

(defn round-open? [round]
  (shared-utils/active-round? round (shared-utils/now-secs)))

(defn default-enabled? [donation]
  "returns if a donation is enabled by default.
  It checks if the sender's discord account is verified"
  (->> donation
       :donation/sender
       :user/socials
       (filter #(and (:social/verified %) (= (name :discord) (:social/network %))))
       seq
       some?))

(defn default-factor [receiver]
  "returns the multiplier factor a receiver should have based on her verified networks"
  (->> receiver
       :user/socials
       (filter #(:social/verified %))
       (map #(:social/network %))
       set
       (clojure.set/intersection #{(name :discord) (name :twitter) (name :eth)})
       count
       (nth (sort multiplier-factors))))

(defn donation-entry [{:keys [:donation/id :donation/sender :donation/amount :donation/date] :as donation} disabled?]
  (let [enabled? (subscribe [::r-subs/donation id])]
    (fn [_ disabled?]
      (let [nav-sender (partial nav-anchor {:route :route.profile/index :params {:address (:user/address sender)}})
            enabled? (if (nil? @enabled?) (default-enabled? donation) @enabled?)
            name (ui-utils/user-or-address (:user/name sender) (:user/address sender))]
        [:div.donation
         {:class (when (or disabled? (not enabled?)) "disabled")}
         [:div.cell.col-sender
          [:span.name [nav-sender [:span name]]
           [social-links {:socials (filter #(:social/verified %) (:user/socials sender))
                          :class "cel"}]]]
         [:div.cell.col-date
          [:span (ui-utils/format-graphql-time date)]]
         [:div.cell.col-amount
          [:span (shared-utils/format-price amount {:coin/decimals 18 :coin/symbol "ETH"})]]
         [:div.cell.col-include [:span.checkmark
               {:on-click #(dispatch [::r-events/enable-donation {:id id :enabled? (not enabled?)}])
                :class (when enabled? "checked")}]]]))))

(defn multipliers [receiver values]
  (let [id (:user/address receiver)
        factor (subscribe [::r-subs/multiplier id])]
    (fn []
      (let [factor (if (nil? @factor) (default-factor receiver) @factor)]
        [:div.factor
         (doall
           (for [value (sort #(compare %2 %1) values)]
             (let [key (str id "-" value)]
               ^{:key key}
               [:<>
                [:input {:type :radio
                         :id key
                         :name key
                         :value value
                         :on-change #(dispatch [::r-events/set-multiplier {:id id :factor value}])
                         :checked (= value factor)
                         }]
                [:label {:for key :title (str "factor " value)}]])))]))))

(defn receiver-entry [{:keys [:user/address :user/name :user/photo :user/socials] :as receiver} donations matchings coin]
  (let [nav-receiver (partial nav-anchor {:route :route.profile/index :params {:address (:user/address receiver)}})
        matching (get matchings address)
        disabled? (= matching "0")]
    [:<>
     [:div.receiver
      {:class (when disabled? "disabled")}
      [:div.cell.col-receiver
       [nav-receiver [user-photo {:class "lb" :src photo}]]
       [:span.name
        [nav-receiver [:h3 (ui-utils/user-or-address name address)]]
        [social-links {:socials (filter #(:social/verified %) socials)
                       :class "cel"}]]]
      [:div.cell.col-matching [:span (shared-utils/format-price matching coin)]]
      [:div.cell.col-multiplier
       [multipliers receiver multiplier-factors]]]
     [:div.donationsInner
       [:div.headerDonations.d-none.d-md-flex
        [:div.cel-data
         [:span.titleCel.col-sender "Sender"]
         [:span.titleCel.col-date "Date"]
         [:span.titleCel.col-amount "Amount"]
         [:span.titleCel.col-include "Include"]]]
       (doall
         (for [{:keys [:donation/id] :as donation} donations]
           ^{:key id} [donation-entry donation disabled?]
           ))]]))


(defn donation-enabled? [donation donations]
  (let [enabled? (get donations (:donation/id donation))]
    (if (nil? enabled?)
      (default-enabled? donation)
      enabled?)))

(defn matching-factor [receiver multipliers]
  (let [factor (get multipliers (:user/address receiver))]
    (if (nil? factor)
      (default-factor receiver)
      factor)))

(def new-bn js/BigNumber.)

; computes the matching belonging to each receiver based on the received donations.
; Some donations may be disabled, hence not counting for the computation. Additionally, each receiver has an individual
; matching-factor, which cap the percentage of the pool the receiver can obtain.
(defn compute-matchings [matching-pool donations-by-receiver multipliers donations-enabled]
  (let [; aggregates the donations using quadratic funding formula but ignoring disabled donations.
        ; (Σ (√(donation_i))²
        ; builds a dict: receiver-id -> donation-amount
        amounts (reduce-kv (fn [m receiver donations]
                             (assoc m
                               (:user/address receiver)
                               (-> (reduce (fn [acc donation]
                                             (if (donation-enabled? donation donations-enabled)
                                               (bn/+ acc (bn/sqrt (new-bn (:donation/amount donation))))
                                               acc))
                                           (new-bn 0) donations)
                                   (bn/pow 2))))
                           {} donations-by-receiver)
        ; fetch the multiplier factor given to each receiver. Builds a dict: receiver-id -> multiplier
        receivers-multipliers (into {} (map (fn [receiver]
                                              [(:user/address receiver)
                                               (matching-factor receiver multipliers)]) (keys donations-by-receiver)))
        ; in case there are not any receiver with the max multiplier (1), the multipliers are then incremented proportionally
        ; so the whole matching pool is distributed.
        max-multiplier (apply max (vals receivers-multipliers))
        receivers-multipliers (if (= max-multiplier 1)
                                receivers-multipliers
                                (let [factor (/ 1 max-multiplier)]
                                  (reduce-kv (fn [m k v]
                                               (assoc m k (* factor v))
                                               ) {} receivers-multipliers)))
        ; for convenience, builds a list of unique defined multipliers, e.g, [0.33 0.66 1]
        multipliers-vals (sort (distinct (vals receivers-multipliers)))
        ; gets the sum of the receivers aggregated amounts, group by multipliers.
        ; builds a dict: multiplier -> sum
        summed-by-multiplier (into {} (map (fn [multiplier]
                                             {multiplier
                                              (reduce-kv (fn [acc receiver amount]
                                                           (if (>= (get receivers-multipliers receiver) multiplier)
                                                             (bn/+ amount acc)
                                                             acc)) (new-bn 0) amounts)})
                                           multipliers-vals))
        matching-pool (new-bn matching-pool)
        ; distributed the matching pool based on the granted amounts and multipliers.
        ; for each receiver, it gets all its applicable multipliers values (i.e., multiplier <= receiver-multiplier).
        ; and for each of them we compute the proportional amount she receives based on its donations
        ; builds a dict: receiver-id -> matching-amount
        receivers-matchings (reduce-kv (fn [m receiver amount]
                                         (assoc m receiver
                                                  (loop [multipliers (filter (fn [multiplier]
                                                                               (<= multiplier (get receivers-multipliers receiver)))
                                                                             multipliers-vals)
                                                         prev_mult 0
                                                         acc (new-bn 0)]
                                                    (let [multiplier (first multipliers)]
                                                      (if multiplier
                                                        (let [sum (get summed-by-multiplier multiplier)
                                                              divisor (if (bn/= sum (new-bn 0)) sum (bn// matching-pool sum))]
                                                          (recur (next multipliers) multiplier
                                                                 (bn/+ acc (bn/* amount (bn/* (- multiplier prev_mult) divisor)))))
                                                        (bn/fixed (.integerValue ^js/BigNumber acc js/BigNumber.ROUND_FLOOR)))))))
                                       {} amounts)]
    receivers-matchings))

; this commented out version of compute matchings applies the receiver's matching factor when getting the donations amount.
; In essence is like the receiver has received only a percentage of the total donations.
;(defn compute-matchings [matching-pool donations-by-receiver multipliers donations-enabled]
;  (let [amounts (reduce-kv (fn [m receiver donations]
;                             (assoc m
;                               (:user/address receiver)
;                               (-> (reduce (fn [acc donation]
;                                             (if (donation-enabled? donation donations-enabled)
;                                               (bn/+ acc (bn/sqrt (new-bn (:donation/amount donation))))
;                                               acc))
;                                           (new-bn 0) donations)
;                                   (bn/* (new-bn (matching-factor receiver multipliers)))
;                                   (bn/pow 2))))
;                           {} donations-by-receiver)
;        summed (reduce bn/+ (vals amounts))
;        divisor (if (bn/= (new-bn 0) summed) summed (/ (new-bn matching-pool) summed))]
;    (reduce-kv (fn [m receiver amount]
;                 (assoc m receiver (* divisor amount)))
;               {} amounts)))

(defn donations-entries [round-id round matching-pool donations-search last-round?]
  (let [tx-id (str "distribute_" round-id (-> matching-pool :matching-pool/coin :coin/chain-id) (-> matching-pool :matching-pool/coin :coin/address))
        distribute-tx-pending (subscribe [::tx-id-subs/tx-pending? {:streamtide/distribute tx-id}])
        distribute-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/distribute tx-id}])
        waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/distribute tx-id}])

        all-donations (->> @donations-search
                           (mapcat (fn [r] (-> r :search-donations :items)))
                           (filter (fn [d] (and (-> d :donation/sender :user/blacklisted not)
                                                (-> d :donation/receiver :user/blacklisted not)))))
        donations-by-receiver (group-by :donation/receiver all-donations)
        matchings (compute-matchings (:matching-pool/amount matching-pool) donations-by-receiver
                                     @(subscribe [::r-subs/all-multipliers])
                                     @(subscribe [::r-subs/all-donations]))]
    (pr matching-pool)
    [:<>
     (if (empty? all-donations)
       [no-items-found]
       [:div.donations
        (doall
          (for [[receiver donations] donations-by-receiver]
            ^{:key (:user/address receiver)} [receiver-entry receiver donations matchings (:matching-pool/coin matching-pool)]))])
     (when (and (not (round-open? round))  ; round needs to be closed ...
                (= "0" (:matching-pool/distributed matching-pool)) ; ... and not distributed yet
                (not= "0" (:matching-pool/amount matching-pool)) ; ... and has something to distribute
                last-round? ; ... be the last existing round
                )
      [pending-button {:pending? (or @distribute-tx-pending @waiting-wallet?)
                       :pending-text "Distributing"
                       :disabled (or @distribute-tx-pending @distribute-tx-success? @waiting-wallet?)
                       :class (str "btBasic btBasic-light btDistribute" (when @distribute-tx-success? " btDistributed"))
                       :on-click (fn [e]
                                   (.stopPropagation e)
                                   (dispatch [::r-events/distribute {:send-tx/id tx-id
                                                                     :round round-id
                                                                     :matchings matchings
                                                                     :coin (:matching-pool/coin matching-pool)
                                                                     :chain-id (-> matching-pool :matching-pool/coin :coin/chain-id str)}]))}
       (if @distribute-tx-success? "Distributed" "Distribute")])]))

(defn donations [round-id round last-round?]
  (let [coins (map #(let [coin (:matching-pool/coin %)]
                       {:value (str (:coin/chain-id coin) "#" (:coin/address coin))
                        :label (:coin/symbol coin)})
                   (:round/matching-pools round))
        form-data (r/atom {:round round-id
                           :order-key (:key (first donations-order))
                           :coin (:value (first coins))})]
    (fn []
      (let [donations-search (subscribe [::gql/query {:queries [(build-donations-query @form-data nil)]}
                                       {:id @form-data}])
            loading? (:graphql/loading? (last @donations-search))
            has-more? (-> (last @donations-search) :search-donations :has-next-page)
            end-cursor (-> (last @donations-search) :search-donations :end-cursor)
            matching-pool (first (filter #(let [[chain-id coin-address] (str/split (:coin @form-data) "#")]
                                            (and
                                              (= chain-id (-> % :matching-pool/coin :coin/chain-id str))
                                              (= coin-address (-> % :matching-pool/coin :coin/address))))
                                         (:round/matching-pools round)))
            ; makes sure all items are loaded
            _ (when (and (not loading?) has-more?) (dispatch [::graphql-events/query
                                                            {:query {:queries [(build-donations-query @form-data end-cursor)]}
                                                             :id @form-data}]))]
        [:div.contentDonation
         [:h2 "Donations"]
         [:div.coin-container "Matching pool coin:"
         (if
           (< (count coins) 2)
           [:div.inputField.simple.coin.disabled
            [:span (-> coins first :label)]]
           [:div.custom-select.selectForm.coin
            [select {:form-data form-data
                     :id        :coin
                     :options   coins
                     :class     "options"}]])]
         [:div.headerReceivers.d-none.d-md-flex
          [:div.cel-data
           [:span.titleCel.col-receiver "Receiver"]
           [:span.titleCel.col-matching "Matching Amount"]
           [:span.titleCel.col-multiplier "Multiplier"]]]

         (if (or loading? has-more?)
           [spinner/spin]
           [donations-entries round-id round matching-pool donations-search last-round?])]))))


(defmethod page :route.admin/round []
  (let [active-page-sub (subscribe [::router-subs/active-page])
        round-id (-> @active-page-sub :params :round)
        form-data (r/atom {:amount 0
                           :matching-pool-coin (:value (first matching-pool-coin))
                           :matching-pool-chain (:value (first matching-pool-chain))})
        errors (reaction {:local (cond-> {}
                                         (and (:coin-address @form-data)
                                              (not (ui-utils/valid-address-format? (:coin-address @form-data))))
                                         (assoc :coin-address "Address not valid")
                                         (not (or (empty? (:from-address @form-data))
                                                  (ui-utils/valid-address-format? (:from-address @form-data))))
                                         (assoc :from-address "Address not valid"))})
        tx-id-mp (str "match-pool_" (random-uuid))
        tx-id-cr (str "close-round_" (random-uuid))
        tx-id-ac (str "approve-coin_" (random-uuid))]
    (fn []
      (let [round-info-query (subscribe [::gql/query {:queries [(build-round-info-query {:round/id round-id})]}
                                         {:refetch-on [::r-events/round-closed]}])
            build-round-is-last-query (subscribe [::gql/query {:queries [(build-round-is-last-query {:round/id round-id})]}])
            loading? (or (:graphql/loading? @round-info-query) (:graphql/loading? @build-round-is-last-query))
            round (:round @round-info-query)
            {:keys [:round/start :round/duration :round/matching-pools :round/distributed]} round
            last-round? (-> @build-round-is-last-query :search-rounds :has-next-page not)
            active-account @(subscribe [::accounts-subs/active-account])]
        [app-layout
         [:main.pageSite.pageRound
          {:id "round"}
          [:div.container
           [:h1.titlePage "Round"]
           [:div.headerRound
            [:h2 (str "Round: " round-id)]]
           (if loading?
             [spinner/spin]
             [:div.contentRound
              (let [status (if (round-open? round) "open" (if (not= "0" distributed) "distributed" "closed"))]
                [:div.status
                 {:class status}
                 (str "Status: " status)])
              [:div.start (str "Start Time: " (ui-utils/format-graphql-time start))]
              [:div.end (str "End Time: " (ui-utils/format-graphql-time (+ start duration)))]
              [:div.matching "Matching pool:" [:div.amounts (map (fn [mp] [:span.amount {:key (str (-> mp :matching-pool/coin :coin/chain-id) (-> mp :matching-pool/coin :coin/address))} (shared-utils/format-price (:matching-pool/amount mp) (:matching-pool/coin mp))] ) matching-pools)]]
              [:div.distributed "Distributed amount:" [:div.amounts (map (fn [mp] [:span.amount {:key (str (-> mp :matching-pool/coin :coin/chain-id) (-> mp :matching-pool/coin :coin/address))} (shared-utils/format-price (:matching-pool/distributed mp) (:matching-pool/coin mp))] ) matching-pools)]]
              (when (round-open? round)
                (let [match-pool-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/fill-matching-pool tx-id-mp}])
                      match-pool-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/fill-matching-pool tx-id-mp}])
                      match-pool-waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/fill-matching-pool tx-id-mp}])
                      close-round-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/close-round tx-id-cr}])
                      close-round-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/close-round tx-id-cr}])
                      close-round-waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/close-round tx-id-cr}])
                      approve-coin-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/approve-coin tx-id-ac}])
                      approve-coin-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/approve-coin tx-id-ac}])
                      approve-coin-waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/approve-coin tx-id-ac}])
                      coin-info (subscribe [::r-subs/coin-info (:matching-pool-chain @form-data) (:coin-address @form-data)])]
                  [:<>
                   [:div.form.fillPoolForm
                    [:label.inputField
                     [:span "Amount"]
                     [amount-input {:id :amount
                                    :form-data form-data}]]
                    [:div.custom-select.selectForm
                     [select {:form-data form-data
                              :id        :matching-pool-coin
                              :options   matching-pool-coin
                              :class     "options"}]]
                    [:div.custom-select.selectForm
                     [select {:form-data form-data
                              :id        :matching-pool-chain
                              :options   matching-pool-chain
                              :class     "options"}]]
                    (when (= (:matching-pool-coin @form-data) other-coin-value)
                      [:<>
                       [:label.inputField.coin
                        [:span "Coin Address"]
                        [text-input {:id :coin-address
                                     :placeholder zero-address
                                     :form-data form-data
                                     :errors errors}]]
                       (if @coin-info
                         [:span.coin-symbol (:symbol @coin-info)]
                         [:button.btBasic.btBasic-light.btValidateCoin
                          {:on-click #(dispatch [::r-events/validate-coin {:chain-id (:matching-pool-chain @form-data) :coin-address (:coin-address @form-data)}])
                           :disabled (or (not (:coin-address @form-data))
                                         (not (ui-utils/valid-address-format? (:coin-address @form-data))))}
                          "Validate"])
                       [:label.inputField.coin
                        [:span "From"]
                        [text-input {:id :from-address
                                     :placeholder active-account
                                     :form-data form-data
                                     :errors errors}]]
                       (when (and @coin-info
                                  (or (empty? (:from-address @form-data))
                                      (= (:from-address @form-data) active-account)))
                         (if
                           (bn/>= (new-bn (:allowance @coin-info)) (new-bn (to-base-amount (:amount @form-data) (:decimals @coin-info))))
                           [:button.btBasic.btBasic-light.btApprovedCoin {:disabled true} "Approved"]
                           [pending-button {:pending? (or @approve-coin-tx-pending? @approve-coin-waiting-wallet?)
                                            :pending-text "Approving"
                                            :disabled (or @approve-coin-tx-pending? @approve-coin-tx-success? @approve-coin-waiting-wallet?
                                                          (>= 0 (:amount @form-data))
                                                          (not (or (empty? (:from-address @form-data))
                                                                   (ui-utils/valid-address-format? (:from-address @form-data)))))
                                            :class (str "btBasic btBasic-light btApproveCoin")
                                            :on-click (fn [e]
                                                        (.stopPropagation e)
                                                        (dispatch [::r-events/approve-coin {:send-tx/id tx-id-ac
                                                                                            :amount (:amount @form-data)
                                                                                            :round round-id
                                                                                            :chain-id (:matching-pool-chain @form-data)
                                                                                            :coin-info @coin-info}]))}
                            (if @approve-coin-tx-success? "Approved" "Approve")]))])]
                   [:div.buttons
                    [pending-button {:pending? (or @match-pool-tx-pending? @match-pool-waiting-wallet?)
                                     :pending-text "Filling Up Matching Pool"
                                     :disabled (or @match-pool-tx-pending? @match-pool-tx-success? @match-pool-waiting-wallet?
                                                   @close-round-tx-pending? @close-round-tx-success? @close-round-waiting-wallet?
                                                   (>= 0 (:amount @form-data))
                                                   (and (= (:matching-pool-coin @form-data) other-coin-value)
                                                        (or (not @coin-info)
                                                            (not (or (empty? (:from-address @form-data))
                                                                     (ui-utils/valid-address-format? (:from-address @form-data)))))))
                                     :class (str "btBasic btBasic-light btMatchPool")
                                     :on-click (fn [e]
                                                 (.stopPropagation e)
                                                 (dispatch [::r-events/fill-matching-pool {:send-tx/id tx-id-mp
                                                                                           :amount (:amount @form-data)
                                                                                           :round round-id
                                                                                           :coin-info (when (= (:matching-pool-coin @form-data)
                                                                                                               other-coin-value)
                                                                                                        @coin-info)
                                                                                           :chain-id (:matching-pool-chain @form-data)
                                                                                           :from-address (let [from-address (:from-address @form-data)]
                                                                                                           (if (empty? from-address) active-account from-address))}]))}
                     (if @match-pool-tx-success? "Matching Pool Filled up" "Fill Up Matching Pool")]
                    [pending-button {:pending? (or @close-round-tx-pending? @close-round-waiting-wallet?)
                                     :pending-text "Closing Round"
                                     :disabled (or @close-round-tx-pending? @close-round-tx-success? @close-round-waiting-wallet?)
                                     :class (str "btBasic btBasic-light btCloseRound")
                                     :on-click (fn [e]
                                                 (.stopPropagation e)
                                                 (dispatch [::r-events/close-round {:send-tx/id tx-id-cr
                                                                                    :round round-id}]))}
                     (if @close-round-tx-success? "Round Closed" "Close Round")]]]))
              [donations round-id round last-round?]])]]]))))
