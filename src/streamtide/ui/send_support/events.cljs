(ns streamtide.ui.send-support.events
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
    [cljs-web3-next.core :as web3]
    [cljs-web3-next.eth :as web3-eth]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.ui.web3.queries :as web3-queries]
    [re-frame.core :as re-frame]
    [streamtide.shared.utils :as shared-utils :refer [donations-types-ids]]
    [streamtide.ui.components.error-notification :as error-notification]
    [streamtide.ui.events :as st-events :refer [wallet-chain-interceptors]]
    [streamtide.ui.utils :refer [build-tx-opts]]))

(def abi-reduced-vibemarket-booster-drop (js/JSON.parse "[{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"},{\"internalType\":\"address\",\"name\":\"recipient\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"referrer\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"originReferrer\",\"type\":\"address\"}],\"name\":\"mint\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"getMintPrice\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"stateMutability\":\"view\",\"type\":\"function\"}]"))

(defn build-vibemarket-metadata [db amount drop-address]
  (let [instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-vibemarket-booster-drop drop-address)
        recipient (account-queries/active-account db)
        referrer (contract-queries/contract-address db :streamtide-fwd)
        encoded-abi (web3-eth/encode-abi instance :mint [amount recipient referrer referrer])
        fn-sig (clj->js ["uint16" "address" "bytes"])
        args (clj->js [(donations-types-ids :vibe-market) drop-address encoded-abi])
        abi (.. ^js (web3-queries/web3 db) -eth -abi)]
    (.encodeParameters abi fn-sig args)))

(defn build-metadata [db donations]
  (map (fn [donation]
         (let [user-info (-> donation val :user-info)
               vibe-market? (-> user-info :user/donations-type (= "vibe-market"))]
           (if vibe-market?
             (build-vibemarket-metadata db (-> donation val :original-amount) (-> user-info :user/donation-coin :coin/address))
             "0x")))
       donations))

(re-frame/reg-event-fx
  ::compute-vibe-market-amount
  (fn [{:keys [db]} [_ {:keys [:donation :user-info :send-tx/id] :as data}]]
    (let [[_ {:keys [:amount]}] donation
          drop-address (-> user-info :user/donation-coin :coin/address)
          instance (web3-eth/contract-at (web3-queries/web3 db) abi-reduced-vibemarket-booster-drop drop-address)]
      {:web3/call {:web3 (web3-queries/web3 db)
                   :fns [{:instance instance
                          :fn :getMintPrice
                          :args [amount]
                          :on-success [::compute-vibe-market-amount-success data]
                          :on-error [::st-events/dispatch-n [[::logging/error "Cannot fetch Mint Price for vibe market card"]
                                                             [::error-notification/show-error "Cannot fetch Mint Price for vibe market card"]]]}]}})))

(re-frame/reg-event-fx
  ::compute-vibe-market-amount-success
  (fn [{:keys [db]} [_ {:keys [:donation :user-info :send-tx/id] :as data} amount]]
    (let [[receiver original-amount] donation]
      {:db (update db :amounts assoc receiver {:amount amount :user-info user-info :original-amount (:amount original-amount)})
       :dispatch [::complete-amount {:send-tx/id id}]})))

(re-frame/reg-event-fx
  ::complete-amount
  (fn [{:keys [db]} [_ {:keys [:send-tx/id] :as data}]]
    (when (every? some? (vals (:amounts db)))
      {:dispatch [::send-support-tx {:donations (:amounts db)
                                     :send-tx/id id}]})))

(re-frame/reg-event-fx
  ::compute-amount
  (fn [{:keys [db]} [_ {:keys [:donation :user-info :send-tx/id] :as data}]]
    (let [[receiver {:keys [:amount]}] donation
          vibe-market? (-> user-info :user/donations-type (= "vibe-market"))]
      (if vibe-market?
        {:dispatch [::compute-vibe-market-amount data]}
        (let [amount (web3/to-wei (shared-utils/safe-number-str amount) :ether)]
          {:db (update db :amounts assoc receiver {:amount amount :user-info user-info :original-amount amount})
           :dispatch [::complete-amount {:send-tx/id id}]})))))

(re-frame/reg-event-fx
  ::send-support
  ; Prepare transaction to send donations to patrons
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:donations :send-tx/id :users-info] :as data}]]
    (let [receivers (keys donations)]
      {:db (assoc db :amounts (into {} (map (fn [receiver]{receiver nil}) receivers)))
       :dispatch-n (map (fn [donation] [::compute-amount {:donation donation
                                                          :user-info (get users-info (key donation))
                                                          :send-tx/id id}]) donations)})))

(re-frame/reg-event-fx
  ::send-support-tx
  ; Make transaction to send donations to patrons
  wallet-chain-interceptors
  (fn [{:keys [db]} [_ {:keys [:donations :send-tx/id] :as data}]]
    (let [tx-name (str "Sending donations to patrons")
          active-account (account-queries/active-account db)
          receivers (keys donations)
          amounts (map #(get-in % [:amount]) (vals donations))
          total-amount-wei (->> amounts
                                (map js/BigNumber.)
                                (reduce bn/+)
                                bn/fixed)
          metadata (build-metadata db donations)]
      {:dispatch (if (some #{"0"} amounts)
                   [::logging/error (str "amount cannot be zero")
                    {:user {:id active-account}
                     :donations donations}
                    ::send-support]
                   [::tx-events/send-tx {:instance (contract-queries/instance db :streamtide (contract-queries/contract-address db :streamtide-fwd))
                                         :fn :donate
                                         :args [receivers amounts metadata]
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
