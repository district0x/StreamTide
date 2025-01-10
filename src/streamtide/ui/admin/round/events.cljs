(ns streamtide.ui.admin.round.events
  (:require
    [cljs-web3-next.core :as web3]
    [cljs-web3-next.eth :as web3-eth]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.ui.web3.queries :as web3-queries]
    [re-frame.core :as re-frame]
    [streamtide.shared.utils :refer [to-base-amount abi-reduced-erc20]]
    [streamtide.ui.components.error-notification :as error-notification]
    [streamtide.ui.config :refer [config-map]]
    [streamtide.ui.events :as ui-events :refer [wallet-chain-interceptors connect-wallet check-chain-id]]
    [streamtide.ui.utils :refer [build-tx-opts]]
    [taoensso.timbre :as log]))

(def interceptors [re-frame/trim-v])
(def wallet-multichain-interceptors [connect-wallet (check-chain-id #(:chain-id (second %)))])

(re-frame/reg-event-fx
  ::set-multiplier
  ; Sets the multiplier factor for a receiver
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :factor]}]]
    {:store (assoc-in store [:multipliers id] factor)
     :db (assoc-in db [:multipliers id] factor)}))

(re-frame/reg-event-fx
  ::enable-donation
  ; Enables or disables a donation for a matching round
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :enabled?]}]]
    {:store (assoc-in store [:donations id] enabled?)
     :db (assoc-in db [:donations id] enabled?)}))

(defn side-chain? [chain-id]
  (not= chain-id (-> config-map :web3-chain :chain-id)))

(defn matching-pool-address [chain-id db]
  (if (side-chain? chain-id)
    (let [chain-id (keyword chain-id)]
      (-> config-map :multichain-smart-contracts :contracts chain-id :matching-pool-fwd :address))
    (contract-queries/contract-address db :streamtide-fwd)))

(defn matching-pool-instance [chain-id db]
  (let [contract-address (matching-pool-address chain-id db)
        contract-key (if (side-chain? chain-id) :matching-pool :streamtide)]
    (contract-queries/instance db contract-key contract-address)))

(re-frame/reg-event-fx
  ::distribute
  ; TX to distribute matching pool for last round
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :round :matchings :coin :chain-id] :as data}]]
    (let [tx-name (str "Distribute matching pool for round " round)
          active-account (account-queries/active-account db)
          matchings (filter #(> (last %) 0) matchings)
          [receivers amounts] ((juxt #(map key %) #(map val %)) matchings)
          amounts (map str amounts)
          token (:coin/address coin)
          args (cond-> [receivers amounts token]
                       (side-chain? chain-id) (conj round))]
      (log/debug "matchings" matchings)
      {:dispatch [::tx-events/send-tx {:instance (matching-pool-instance chain-id db)
                                       :fn :distribute
                                       :args args
                                       :tx-opts (build-tx-opts {:from active-account})
                                       :tx-id {:streamtide/distribute id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::distribute]
                                                        [::notification-events/show (str "Matching pool successfully distributed for round " round)]]
                                       :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                        {:user {:id active-account}
                                                         :matchings matchings}
                                                        ::distribute]
                                                       [::error-notification/show-error "Transaction failed"]]
                                       :on-tx-hash-error-n [[::logging/error (str tx-name " tx error")
                                                             {:user {:id active-account}
                                                              :matchings matchings}
                                                             ::distribute]
                                                            [::error-notification/show-error "Transaction failed"]]}]})))

(defn matching-pool-tx-input [{:keys [:amount :coin-info :from-address :chain-id :round]}]
  (if coin-info
    (let [from from-address
          token (:coin-address coin-info)
          amount (to-base-amount amount (:decimals coin-info))]
      {:args (cond-> [from token amount]
                     (side-chain? chain-id) (conj round))
       :amount-wei nil
       :fn :fill-up-matching-pool-token})
    {:args (cond-> []
                   (side-chain? chain-id) (conj round))
     :fn :fill-up-matching-pool
     :amount-wei (-> amount str (web3/to-wei :ether))}))

(re-frame/reg-event-fx
  ::fill-matching-pool
  ; TX to fill up matching pool
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :amount :round :coin-info :from-address :chain-id] :as data}]]
    (let [symbol (if coin-info (:symbol coin-info) "ETH")
          tx-name (str "Filling up matching pool with " amount " " symbol)
          active-account (account-queries/active-account db)
          {:keys [args amount-wei fn]} (matching-pool-tx-input data)]
      {:dispatch [::tx-events/send-tx {:instance (matching-pool-instance chain-id db)
                                       :args args
                                       :fn fn
                                       :tx-opts (build-tx-opts {:from active-account :value amount-wei})
                                       :tx-id {:streamtide/fill-matching-pool id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::fill-matching-pool]
                                                        [::notification-events/show (str "Matching pool successfully filled with " amount " " symbol)]]
                                       :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                        {:user {:id active-account}
                                                         :round round
                                                         :amount amount
                                                         :coin-info coin-info}
                                                        ::fill-matching-pool]
                                                       [::error-notification/show-error "Transaction failed"]]
                                       :on-tx-hash-error-n [[::logging/error (str tx-name " tx error")
                                                             {:user {:id active-account}
                                                              :round round
                                                              :amount amount
                                                              :coin-info coin-info}
                                                             ::fill-matching-pool]
                                                            [::error-notification/show-error "Transaction failed"]]}]})))

(re-frame/reg-event-fx
  ::approve-coin
  ; TX to approve streamtide contract to transfer ERC20 token
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :amount :round :coin-info :chain-id]}]]
    (let [tx-name "Approve coin"
          active-account (account-queries/active-account db)
          amount-wei (to-base-amount amount (:decimals coin-info))
          spender (matching-pool-address chain-id db)]
      {:dispatch [::tx-events/send-tx {:instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-erc20 (:coin-address coin-info))
                                       :fn :approve
                                       :args [spender amount-wei]
                                       :tx-opts (build-tx-opts {:from active-account})
                                       :tx-id {:streamtide/approve-coin id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::approve-coin]
                                                         [::notification-events/show "Coin successfully approved"]
                                                         [::get-allowance coin-info]]
                                       :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                        {:user {:id active-account}
                                                         :round round}
                                                        ::approve-coin]
                                                       [::error-notification/show-error "Transaction failed"]]
                                       :on-tx-hash-error-n [[::logging/error (str tx-name " tx error")
                                                             {:user {:id active-account}
                                                              :round round}
                                                             ::approve-coin]
                                                            [::error-notification/show-error "Transaction failed"]]}]})))

(re-frame/reg-event-fx
  ::close-round
  ; TX to close an ongoing round
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:send-tx/id :round] :as data}]]
    (let [tx-name "Close round"
          active-account (account-queries/active-account db)]
      {:dispatch [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                       :fn :close-round
                                       :args []
                                       :tx-opts (build-tx-opts {:from active-account})
                                       :tx-id {:streamtide/close-round id}
                                       :tx-log {:name tx-name
                                                :related-href {:name :route.admin/round
                                                               :params {:round round}}}
                                       :on-tx-success-n [[::logging/info (str tx-name " tx success") ::close-round]
                                                         [::notification-events/show "Round successfully closed"]
                                                         [::round-closed]]
                                       :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                        {:user {:id active-account}
                                                         :round round}
                                                        ::close-round]
                                                       [::error-notification/show-error "Transaction failed"]]
                                       :on-tx-hash-error-n [[::logging/error (str tx-name " tx error")
                                                             {:user {:id active-account}
                                                              :round round}
                                                             ::close-round]
                                                            [::error-notification/show-error "Transaction failed"]]}]})))

(re-frame/reg-event-fx
  ::round-closed
  ; Event triggered when a round is closed. This is an empty event just aimed for subscription
  (constantly nil))

(re-frame/reg-event-fx
  ::validate-coin
  ; Fetch ERC20 info if valid
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:chain-id :coin-address] :as args}]]
    (try
      ; because of the interceptors, we assume user is connected to the right chain when executing this code
      (let [instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-erc20 coin-address)]
        {:web3/call {:web3 (web3-queries/web3 db)
                     :fns [{:instance instance
                            :fn :decimals
                            :args []
                            :on-success [::get-coin-symbol args]
                            :on-error [::ui-events/dispatch-n [[::logging/error "Cannot fetch ERC20 token details"]
                                                               [::error-notification/show-error "Cannot fetch ERC20 token details"]]]}]}})
      (catch :default e
        {:dispatch-n [[::logging/error "Cannot parse address" {:error e}]
                      [::error-notification/show-error "Cannot parse address" e]]}))))

(re-frame/reg-event-fx
  ::get-coin-symbol
  ; Get the symbol of an ERC20 token
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:chain-id :coin-address] :as args} decimals]]
    (let [instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-erc20 coin-address)]
      {:web3/call {:web3 (web3-queries/web3 db)
                   :fns [{:instance instance
                          :fn :symbol
                          :args []
                          :on-success [::ui-events/dispatch-n
                                       [[::get-coin-symbol-success (merge args {:decimals decimals})]
                                        [::get-allowance args]]]
                          :on-error [::ui-events/dispatch-n [[::logging/error "Cannot fetch ERC20 token symbol"]
                                                             [::error-notification/show-error "Cannot fetch ERC20 token symbol"]]]}]}})))

(re-frame/reg-event-fx
  ::get-coin-symbol-success
  ; called when fetching a coin name
  [interceptors]
  (fn [{:keys [db]} [{:keys [:chain-id :coin-address] :as args} [symbol]]]
    {:db (assoc-in db [:coin-info chain-id coin-address] (merge args {:symbol symbol}))}))

(re-frame/reg-event-fx
  ::get-allowance
  ; Get the allowance the streamtide contract has been given by the current user to spend a given ERC20 token
  wallet-multichain-interceptors
  (fn [{:keys [db]} [_ {:keys [:chain-id :coin-address] :as args}]]
    (let [instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-erc20 coin-address)
          owner (account-queries/active-account db)
          spender (matching-pool-address chain-id db)]
      {:web3/call {:web3 (web3-queries/web3 db)
                   :fns [{:instance instance
                          :fn :allowance
                          :args [owner spender]
                          :on-success [::get-allowance-success args]
                          :on-error [::ui-events/dispatch-n [[::logging/error "Cannot fetch ERC20 allowance"]
                                                             [::error-notification/show-error "Cannot fetch ERC20 allowance"]]]}]}})))

(re-frame/reg-event-fx
  ::get-allowance-success
  ; called when fetching allowance
  [interceptors]
  (fn [{:keys [db]} [{:keys [:chain-id :coin-address]} allowance]]
    {:db (update-in db [:coin-info chain-id coin-address] #(merge % {:allowance allowance}))}))
