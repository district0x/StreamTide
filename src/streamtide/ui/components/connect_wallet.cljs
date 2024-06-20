(ns streamtide.ui.components.connect-wallet
  (:require
    ["thirdweb/react" :refer [ConnectButton ThirdwebProvider useActiveWallet useActiveAccount useConnect useActiveWalletChain useConnectModal]]
    ["thirdweb/wallets" :refer [createWallet]]
    ["thirdweb" :refer [createThirdwebClient waitForReceipt]]
    ["thirdweb/utils" :refer [toHex]]
    ["thirdweb/rpc" :refer [getRpcClient, eth_getTransactionByHash eth_getTransactionReceipt eth_blockNumber eth_getBlockByNumber]]
    ["react" :as react]
    [camel-snake-kebab.core :as csk]
    [camel-snake-kebab.extras :refer [transform-keys]]
    [cljs-web3-next.core :as web3-next]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [district.ui.web3-accounts.events :as accounts-events]
    [district.ui.web3-chain.events :as chain-events]
    [district.ui.web3-chain.subs :as chain-subs]
    [district.ui.web3.events :as web3-events]
    [eip55.core :as eip55]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.config :refer [config-map]]))


(defn build-chain-info []
  (clj->js (-> config-map
               :web3-chain
               (update :chain-id int)
               (update :rpc-urls first)
               (clojure.set/rename-keys {:chain-id :id
                                         :rpc-urls :rpc
                                         :chain-name :name
                                         :native-currency :nativeCurrency}))))

(defn- parse-tx [receipt]
  (let [receipt (js->clj receipt :keywordize-keys true)
        receipt (clojure.walk/postwalk
                  (fn [x]
                    (if (or (number? x) (= (goog/typeOf x) "bigint"))
                      (toHex x)
                      x))
                  receipt)]
    (cond-> receipt
            (= "success" (:status receipt)) (assoc :status "0x1")
            (= "reverted" (:status receipt)) (assoc :status "0x0")
            (contains? receipt :type) (update :type (fn [type typeHex]
                                                      (if typeHex
                                                        typeHex
                                                        (case type
                                                          "legacy" "0x0"
                                                          "eip2930" "0x1"
                                                          "eip1559" "0x2"
                                                          "eip4844" "0x3"
                                                          "0x0"))) (:typeHex receipt))
            true clj->js)))

(defn create-provider [^js wallet ^js connectModal ^js connect-config]
  #js {:request (fn [^js args]
                  (print args)
                  (case (.-method args)
                    "eth_chainId" (js/Promise. (fn [] (-> wallet .getChain .-id)))
                    "eth_sendTransaction" (if (nil? wallet)
                                            ((.-connect connectModal) connect-config)
                                            (js/Promise. (fn [resolve reject]
                                                           (let [chainId (-> wallet .getChain .-id)
                                                                 params (js->clj (aget (.-params args) 0) :keywordize-keys true)
                                                                 params (merge params {:chainId chainId})
                                                                 params (dissoc params :gasPrice :gas)
                                                                 params (clj->js params)]
                                                             (-> wallet .getAccount (.sendTransaction params)
                                                                 (.then (fn [res]
                                                                          (resolve (.-transactionHash res))))
                                                                 (.catch (fn [err]
                                                                           (reject err))))))))
                    "personal_sign" (if (nil? wallet)
                                      ((.-connect connectModal) connect-config)
                                      (let [account (aget (.-params args) 1)
                                            data-raw (aget (.-params args) 0)
                                            data (web3-next/to-ascii data-raw)
                                            params (clj->js {:account account
                                                             :message {:raw data}})]
                                        (-> wallet .getAccount (.signMessage params))))
                    "eth_getTransactionReceipt" (js/Promise. (fn [resolve reject]
                                                               (let [rpc-client (getRpcClient #js {:client (.-client connect-config)
                                                                                                   :chain  (build-chain-info)})]
                                                                 (-> (eth_getTransactionReceipt rpc-client #js {:hash (-> args .-params (aget 0))})
                                                                     (.then (fn [res]
                                                                              (resolve (parse-tx res))))
                                                                     (.catch (fn [err]
                                                                               (reject err)))))))
                    "eth_getTransactionByHash" (js/Promise. (fn [resolve reject]
                                                              (let [rpc-client (getRpcClient #js {:client (.-client connect-config)
                                                                                                  :chain  (build-chain-info)})]
                                                                (-> (eth_getTransactionByHash rpc-client #js {:hash (-> args .-params (aget 0))})
                                                                    (.then (fn [res]
                                                                             (resolve (parse-tx res))))
                                                                    (.catch (fn [err]
                                                                              (reject err)))))))
                    "wallet_switchEthereumChain" (.switchChain wallet (build-chain-info))
                    "eth_blockNumber" (js/Promise. (fn [resolve reject]
                                                     (let [rpc-client (getRpcClient #js {:client (.-client connect-config)
                                                                                         :chain  (build-chain-info)})]
                                                       (-> (eth_blockNumber rpc-client)
                                                           (.then (fn [res]
                                                                    (resolve (toHex res))))
                                                           (.catch (fn [err]
                                                                     (reject err)))))))
                    "eth_requestAccounts" (if (nil? wallet)
                                            ((.-connect connectModal) connect-config)
                                            (js/Promise. (fn [resolve reject]
                                                           (resolve [(-> wallet .getAccount .-address)]))))
                    "eth_getBlockByNumber" (js/Promise. (fn [resolve reject]
                                                          (let [rpc-client (getRpcClient #js {:client (.-client connect-config)
                                                                                              :chain  (build-chain-info)})]
                                                            (-> (eth_getBlockByNumber rpc-client {:blockNumber         (-> args .-params (aget 0))
                                                                                                  :includeTransactions (-> args .-params (aget 1))})
                                                                (.then (fn [res]
                                                                         (resolve (parse-tx res))))
                                                                (.catch (fn [err]
                                                                          (reject err)))))))
                    (js/Promise.reject (str "Method not implemented: " (.-method args)))))})

(re-frame/reg-event-fx
  :logged-in?
  (fn [{:keys [db]} [_ {:keys [:user/address :callback]}]]
    (let [active-session (get db :active-session)]
      {:callback {:fn callback
                  :result (= address (:user/address active-session))}})))

(defn connect-wallet [{:keys [:class]}]
  (let [day-night (subscribe [::st-subs/day-night-switch])
        active-account (subscribe [::accounts-subs/active-account])
        active-chain (subscribe [::chain-subs/chain])
        active-wallet (useActiveWallet)
        loaded? (not (.-isConnecting (useConnect)))
        account (useActiveAccount)
        chain (useActiveWalletChain)
        client (createThirdwebClient #js {:clientId (-> config-map :thirdweb :client-id)})
        connect-config (clj->js {:auth {:doLogin (fn [^js signedPayload]
                                                   (js/Promise. (fn [resolve reject]
                                                                  (dispatch [:user/-authenticate
                                                                             {:payload (transform-keys csk/->kebab-case (js->clj (.-payload signedPayload) :keywordize-keys true))
                                                                              :signature (.-signature signedPayload)
                                                                              :callback (fn [err res]
                                                                                          (if err
                                                                                            (reject err)
                                                                                            (resolve res)))}]))))
                                        :getLoginPayload (fn [^js account]
                                                           (js/Promise.
                                                             (fn [resolve reject]
                                                               (dispatch [:user/request-login-payload
                                                                          {:address (eip55/address->checksum (.-address account))
                                                                           :chain-id (.-chainId account)
                                                                           :callback
                                                                           (fn [err res]
                                                                             (if err
                                                                               (reject err)
                                                                               (resolve (clj->js
                                                                                          (into {}
                                                                                                (filter (fn [[_ v]]
                                                                                                          (some? v))
                                                                                                        (transform-keys csk/->snake_case res)))))))}]))))
                                        :isLoggedIn (fn [address]
                                                      (js/Promise. (fn [resolve reject]
                                                                     (dispatch [:logged-in? {:user/address (eip55/address->checksum address)
                                                                                             :callback (fn [err res]
                                                                                                         (if err
                                                                                                           (reject err)
                                                                                                           (resolve res)))}]))))
                                        :doLogout (fn []
                                                    (js/Promise. (fn [resolve]
                                                                   (dispatch [:user/sign-out {:callback (fn []
                                                                                                          (resolve))}]))))}
                                 :connectButton {:className class}
                                 :detailsButton {:className class}
                                 :switchButton {:className class}
                                 :signInButton {:className class}
                                 :client client
                                 :theme (if (= @day-night "day") "light" "dark")
                                 :wallets [
                                           (createWallet "io.metamask")
                                           (createWallet "com.coinbase.wallet")
                                           (createWallet "io.rabby")
                                           (createWallet "walletConnect")]
                                 :showAllWallets false
                                 :chain (build-chain-info)
                                 :switchToActiveChain true})
        connectModal (useConnectModal)
        provider (create-provider active-wallet connectModal connect-config)]
    ;; some libraries assume the provider is in window.ethereum, so we set our wrapper in there to intercept any call

    (set! (.-ethereum js/window) provider)
    (dispatch [::web3-events/create-web3-with-user-permitted-provider {} provider])
    (react/useEffect (fn []
                       (when (and loaded? active-wallet)
                         (dispatch [::chain-events/set-chain (-> active-wallet .getChain .-id)])
                         (dispatch [::accounts-events/set-accounts [(-> active-wallet .getAccount .-address)]]))
                         js/undefined)
                   (array active-wallet))
  (react/useEffect (fn []
                     (when loaded?
                       (let [account-address (if account (.-address account) nil)]
                         (if account-address
                           (when (not= account-address @active-account)
                             (dispatch [::accounts-events/set-accounts [account-address]]))
                           (when @active-account
                             (dispatch [::accounts-events/set-accounts []])))))
                       js/undefined)
                   (array account))
  (react/useEffect (fn []
                     (when (and loaded? chain)
                       (let [chain-id (.-id chain)]
                           (when (not= chain-id @active-chain)
                             (dispatch [::chain-events/set-chain chain-id]))))
                       js/undefined)
                   (array chain))
  (r/create-element ConnectButton
                    connect-config)))


(defn connect-wallet-btn [{:keys [:class] :as opts}]
    (r/create-element ThirdwebProvider
      #js {} (r/as-element [:f> connect-wallet opts])))
