(ns streamtide.ui.components.connect-wallet
  (:require
    ["thirdweb/react" :refer [ConnectButton ThirdwebProvider useActiveWallet useActiveAccount useConnect useActiveWalletChain]]
    ["thirdweb/wallets" :refer [createWallet inAppWallet]]
    ["thirdweb" :refer [createThirdwebClient defineChain]]
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

(defn create-provider [^js wallet]
  #js {:request (fn [^js args]
                  (case (.-method args)
                    "eth_chainId" (js/Promise. (fn [] (-> wallet .getChain .-id)))
                    "eth_sendTransaction" (let [chainId (-> wallet .getChain .-id)
                                                params (js->clj (aget (.-params args) 0) :keywordize-keys true)
                                                params (merge params {:chainId chainId})
                                                params (dissoc params :gasPrice :gas)
                                                params (clj->js params)]
                                            (-> wallet .getAccount (.sendTransaction params )))
                    "personal_sign" (let [account (aget (.-params args) 1)
                                          data-raw (aget (.-params args) 0)
                                          data (web3-next/to-ascii data-raw)
                                          params (clj->js {:account account
                                                           :message {:raw data}})
                                          response (-> wallet .getAccount (.signMessage params))]
                                      response)
                    ;"eth_requestAccounts" ""
                    "wallet_switchEthereumChain" (.switchChain wallet (build-chain-info))
                    (js/Error. (str "Method not implemented: " (.-method args)))))})

(re-frame/reg-event-fx
  :logged-in?
  (fn [{:keys [db]} [_ {:keys [:user/address :callback]}]]
    (let [active-session (get db :active-session)]
      {:callback {:fn callback
                  :result (= address (:user/address active-session))}})))

(defn connect-wallet [{:keys [:class]}]
  (let [
        day-night (subscribe [::st-subs/day-night-switch])
        active-account (subscribe [::accounts-subs/active-account])
        active-chain (subscribe [::chain-subs/chain])
        active-wallet (useActiveWallet)
        loaded? (not (.-isConnecting (useConnect)))
        account (useActiveAccount)
        chain (useActiveWalletChain)
        client (createThirdwebClient #js {:clientId "f478f4123340f16303e57df57b6e26ef"})]
    (react/useEffect (fn []
                     (when (and loaded? active-wallet)
                       (let [provider (create-provider active-wallet)]
                         (set! (.-ethereum js/window) provider)
                         (dispatch [::web3-events/create-web3-with-user-permitted-provider {} provider])
                         (dispatch [::chain-events/set-chain (-> active-wallet .getChain .-id)])
                         (dispatch [::accounts-events/set-accounts [(-> active-wallet .getAccount .-address)]])))
                       js/undefined)
                   (array active-wallet))
  (react/useEffect (fn []
                     (when (and loaded?)
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
                    (clj->js {
                              :auth {
                                     :doLogin (fn [^js signedPayload]
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
                         :switchToActiveChain true}))))


(defn connect-wallet-btn [{:keys [:class] :as opts}]
    (r/create-element ThirdwebProvider
      #js {} (r/as-element [:f> connect-wallet opts])))
