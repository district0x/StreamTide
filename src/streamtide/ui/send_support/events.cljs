(ns streamtide.ui.send-support.events
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
    [cljs-web3-next.core :as web3]
    [cljs-web3-next.eth :as web3-eth]
    [cljs-web3-next.helpers :refer [zero-address]]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.ui.web3.queries :as web3-queries]
    [re-frame.core :as re-frame]
    [streamtide.shared.utils :as shared-utils :refer [donations-types-ids]]
    [streamtide.ui.components.error-notification :as error-notification]
    [streamtide.ui.config :refer [config-map]]
    [streamtide.ui.events :as st-events :refer [wallet-chain-interceptors]]
    [streamtide.ui.utils :refer [build-tx-opts]]))

(def abi-reduced-vibemarket-booster-drop (js/JSON.parse "[{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"},{\"internalType\":\"address\",\"name\":\"recipient\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"referrer\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"originReferrer\",\"type\":\"address\"}],\"name\":\"mint\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"getMintPrice\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"stateMutability\":\"view\",\"type\":\"function\"}]"))

(def abi-reduced-toshimart-portal (js/JSON.parse "[{\"inputs\":[{\"components\":[{\"internalType\":\"address\",\"name\":\"inputToken\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"outputToken\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"inputAmount\",\"type\":\"uint256\"}],\"name\":\"params\",\"type\":\"tuple\"}],\"name\":\"quoteExactInput\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"outputAmount\",\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"components\":[{\"internalType\":\"address\",\"name\":\"inputToken\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"outputToken\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"inputAmount\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"minOutputAmount\",\"type\":\"uint256\"},{\"internalType\":\"bytes\",\"name\":\"permitData\",\"type\":\"bytes\"},{\"internalType\":\"bytes\",\"name\":\"extensionData\",\"type\":\"bytes\"}],\"name\":\"params\",\"type\":\"tuple\"}],\"name\":\"swapExactInputV3\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"outputAmount\",\"type\":\"uint256\"}],\"stateMutability\":\"payable\",\"type\":\"function\"}]"))

(defn build-vibemarket-metadata [db amount-token drop-address]
  (let [instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-vibemarket-booster-drop drop-address)
        recipient (account-queries/active-account db)
        referrer (contract-queries/contract-address db :streamtide-fwd)
        encoded-abi (web3-eth/encode-abi instance :mint [amount-token recipient referrer referrer])
        fn-sig (clj->js ["uint16" "address" "bytes"])
        args (clj->js [(donations-types-ids :vibe-market) drop-address encoded-abi])
        abi (.. ^js (web3-queries/web3 db) -eth -abi)]
    (.encodeParameters abi fn-sig args)))

(defn build-toshimart-metadata [db wei-amount token-amount contract-address]
  (let [portal-address (-> config-map :toshi-mart :portal-address)
        instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-toshimart-portal portal-address)
        params {:inputToken zero-address
                :outputToken contract-address
                :inputAmount wei-amount
                :minOutputAmount token-amount
                :permitData "0x"
                :extensionData "0x"}
        encoded-abi (web3-eth/encode-abi instance :swapExactInputV3 [params])
        fn-sig (clj->js ["uint16" "address" "bytes"])
        args (clj->js [(donations-types-ids :toshi-mart) portal-address encoded-abi])
        abi (.. ^js (web3-queries/web3 db) -eth -abi)]
    (.encodeParameters abi fn-sig args)))

(defn build-metadata [db donations]
  (map (fn [{:keys [:user-info :donation-data] :as donation}]
         (let [donations-type (:user/donations-type user-info)]
           (case donations-type
             "vibe-market" (build-vibemarket-metadata db (:amount-token donation-data) (-> user-info :user/donation-coin :coin/address))
             "toshi-mart" (build-toshimart-metadata db (:amount-wei donation-data) (:amount donation-data) (-> user-info :user/donation-coin :coin/address))
             "0x")))
       donations))

(defn build-tokens [_db donations]
  (map (fn [{:keys [:user-info] :as donation}]
         (let [donations-type (:user/donations-type user-info)]
           (case donations-type
             "toshi-mart" (-> user-info :user/donation-coin :coin/address)
             zero-address)))
       donations))

(defn compute-vibe-market-amount [web3 amount drop-address on-success]
  (let [instance (web3-eth/contract-at web3 abi-reduced-vibemarket-booster-drop drop-address)]
    {:web3/call {:web3 web3
                 :fns [{:instance instance
                        :fn :getMintPrice
                        :args [amount]
                        :on-success on-success
                        :on-error [::st-events/dispatch-n [[::logging/error "Cannot fetch Mint Price for vibe market card"]
                                                           [::error-notification/show-error "Cannot fetch Mint Price for vibe market card"]]]}]}}))

(defn compute-toshi-mart-amount [web3 amount contract-address on-success]
  (let [portal-address (-> config-map :toshi-mart :portal-address)
        instance (web3-eth/contract-at web3 abi-reduced-toshimart-portal portal-address)
        params {:inputToken zero-address
                :outputToken contract-address
                :inputAmount amount}]
    {:web3/call {:web3 web3
                 :fns [{:instance instance
                        :fn :quoteExactInput
                        :args [params]
                        :on-success on-success
                        :on-error [::st-events/dispatch-n [[::logging/error "Cannot fetch trade Price for toshi mart token"]
                                                           [::error-notification/show-error "Cannot fetch trade Price for toshi mart token"]]]}]}}))

(re-frame/reg-event-fx
  ::compute-vibe-market-price
  (fn [{:keys [db]} [_ {:keys [:contract-address] :as data}]]
      (compute-vibe-market-amount (web3-queries/web3 db)
                                  1
                                  contract-address
                                  [::compute-coin-conversion-success data])))

(re-frame/reg-event-fx
  ::compute-toshi-mart-price
  (fn [{:keys [db]} [_ {:keys [:contract-address :amount-eth] :as data}]]
    (compute-toshi-mart-amount (web3-queries/web3 db)
                               (web3/to-wei (str amount-eth) :ether)
                               contract-address
                               [::compute-coin-amount-conversion-success data])))

(re-frame/reg-event-fx
  ::compute-coin-conversion-success
  (fn [{:keys [db]} [_ {:keys [:contract-address] :as data} amount]]
    {:db (update db :coin-conversion assoc (keyword contract-address) amount)}))

(re-frame/reg-event-fx
  ::compute-coin-amount-conversion-success
  (fn [{:keys [db]} [_ {:keys [:user-address :contract-address :amount-eth :amount-path] :as data} amount]]
    (when (= (get-in db [:coin-amount-conversion-in-progress (keyword user-address) (keyword contract-address)]) amount-eth)
      {:db (-> db
               (update :coin-conversion assoc-in [(keyword user-address) (keyword contract-address)] amount)
               (dissoc :coin-amount-conversion-in-progress (keyword user-address) (keyword contract-address)))})))

(re-frame/reg-event-fx
  ::set-coin-conversion-in-progress
  (fn [{:keys [db]} [_ {:keys [:user-address :contract-address :amount-eth] :as data}]]
    {:db (assoc-in db [:coin-amount-conversion-in-progress (keyword user-address) (keyword contract-address)] amount-eth)}))

(re-frame/reg-event-fx
  ::compute-vibe-market-amount
  (fn [{:keys [db]} [_ {:keys [:receiver :amount-form-info :user-info :send-tx/id] :as data}]]
    (let [{:keys [:amount-token]} amount-form-info
          drop-address (-> user-info :user/donation-coin :coin/address)]
      (compute-vibe-market-amount (web3-queries/web3 db)
                                  amount-token
                                  drop-address
                                  [::compute-amount-success (merge data {:donation-data {:amount-token amount-token}})]))))

(re-frame/reg-event-fx
  ::compute-toshi-mart-amount
  (fn [{:keys [db]} [_ {:keys [:receiver :amount-form-info :user-info :send-tx/id] :as data}]]
    (let [{:keys [:amount-eth]} amount-form-info
          coin (:user/donation-coin user-info)
          contract-address (:coin/address coin)
          amount-wei (web3/to-wei amount-eth :ether)]
      (compute-toshi-mart-amount (web3-queries/web3 db)
                                 amount-wei
                                 contract-address
                                 [::compute-amount-success (merge data {:donation-data {:amount-wei amount-wei}})]))))

(re-frame/reg-event-fx
  ::compute-amount-success
  (fn [{:keys [db]} [_ {:keys [:receiver :user-info :send-tx/id] :as data} amount]]
    {:dispatch [::complete-amount (update data :donation-data merge {:amount amount})]}))

(re-frame/reg-event-fx
  ::complete-amount
  (fn [{:keys [db]} [_ {:keys [:receiver :send-tx/id] :as data}]]
    (let [new-db (assoc-in db [:donations-infos receiver] data)
          amounts (:donations-infos new-db)
          done? (every? some? (vals amounts))]
      (merge
        {:db new-db}
        (when done?
          {:dispatch [::send-support-tx {:donations amounts
                                         :send-tx/id id}]})))))

(re-frame/reg-event-fx
  ::compute-amount
  (fn [{:keys [db]} [_ {:keys [:receiver :amount-form-info :user-info :send-tx/id] :as data}]]
    (let [donations-type (:user/donations-type user-info)]
      (case donations-type
        "vibe-market" {:dispatch [::compute-vibe-market-amount data]}
        "toshi-mart" {:dispatch [::compute-toshi-mart-amount data]}
        (let [amount (web3/to-wei (shared-utils/safe-number-str (:amount-eth amount-form-info)) :ether)]
          {:dispatch [::complete-amount (merge data {:donation-data {:amount-wei amount}})]})))))

(re-frame/reg-event-fx
  ::send-support
  ; Prepare transaction to send donations to patrons
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:donations :send-tx/id :users-info] :as data}]]
    (let [receivers (keys donations)]
      {:db (assoc db :donations-infos (zipmap receivers (repeat nil)))
       :dispatch-n (map (fn [donation] [::compute-amount {:receiver (key donation)
                                                          :amount-form-info (val donation)
                                                          :user-info (get users-info (key donation))
                                                          :send-tx/id id}]) donations)})))

(defn get-wei-amounts [{:keys [:donation-data :user-info]}]
  (let [donations-type (:user/donations-type user-info)]
    (case donations-type
      "vibe-market" (:amount donation-data)
      (:amount-wei donation-data))))

(re-frame/reg-event-fx
  ::send-support-tx
  ; Make transaction to send donations to patrons
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:donations :send-tx/id] :as data}]]
    (let [tx-name (str "Sending donations to patrons")
          active-account (account-queries/active-account db)
          [receivers donations-infos] ((juxt keys vals) donations)
          amounts (map #(get-wei-amounts %) donations-infos)
          total-amount-wei (->> amounts
                                (map js/BigNumber.)
                                (reduce bn/+)
                                bn/fixed)
          metadata (build-metadata db donations-infos)
          tokens (build-tokens db donations-infos)]
      {:dispatch (if (some #{"0"} amounts)
                   [::logging/error (str "amount cannot be zero")
                    {:user {:id active-account}
                     :donations donations}
                    ::send-support]
                   [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                         :fn :donate
                                         :args [receivers amounts metadata tokens]
                                         :tx-opts (build-tx-opts {:from active-account :value total-amount-wei})
                                         :tx-id {:streamtide/donate id}
                                         :tx-log {:name tx-name
                                                  :related-href {:name :route.send-support/index}}
                                         :on-tx-success-n [[::logging/info (str tx-name " tx success") ::send-support]
                                                           [::notification-events/show "Donations successfully sent"]
                                                           [::send-support-success]]
                                         :on-tx-error-n [[::logging/error (str tx-name " tx error")
                                                          {:user {:id active-account}
                                                           :donations donations}
                                                          ::send-support]
                                                         [::error-notification/show-error "Transaction failed"]]
                                         :on-tx-hash-error-n [[::logging/error (str tx-name " tx error")
                                                               {:user {:id active-account}
                                                                :donations donations}
                                                               ::send-support]
                                                              [::error-notification/show-error "Transaction failed"]]}])})))

(re-frame/reg-event-fx
  ::send-support-success
  (fn [{:keys [db]} [_]]
    {:dispatch [::st-events/clean-cart]}))
