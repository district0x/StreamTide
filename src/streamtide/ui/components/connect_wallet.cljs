(ns streamtide.ui.components.connect-wallet
  (:require ["@thirdweb-dev/react" :refer [ConnectWallet ThirdwebProvider NetworkSelector metamaskWallet coinbaseWallet walletConnect
                                           ;rainbowWallet trustWallet
                                           ]]
            ["@thirdweb-dev/chains" :refer [Ethereum, Polygon, Localhost]]
            ["@thirdweb-dev/react-core" :refer [useWallet useAddress useConnectionStatus]]
            ["react" :as react]
            [district.ui.web3-accounts.subs :as accounts-subs]
            [district.ui.web3-accounts.events :as accounts-events]
            [district.ui.web3.events :as web3-events]
            [district.ui.web3.subs :as web3-subs]
            [cljs-web3-next.core :as web3-next]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [streamtide.ui.subs :as st-subs]
            [streamtide.ui.config :refer [config-map]]))


(defn connect-wallet [{:keys [:class]}]
  (let [day-night (subscribe [::st-subs/day-night-switch])
        active-account (subscribe [::accounts-subs/active-account])
        active-web3-provider (subscribe [::web3-subs/web3])
        active-wallet (useWallet)
        loaded? (not= "unknown" (useConnectionStatus))
        account (useAddress)]
  (react/useEffect (fn []
                     (when (and loaded? active-wallet)
                       (-> active-wallet .-connector .getProvider
                           (.then (fn [provider]
                                    (when (or (not @active-web3-provider) (not= provider (web3-next/current-provider @active-web3-provider)))
                                      (dispatch [::web3-events/create-web3-with-user-permitted-provider {} provider]))))))
                       js/undefined)
                   (array active-wallet))
  (react/useEffect (fn []
                     (when loaded?
                       (if account
                         (when (not= account @active-account)
                           (dispatch [::accounts-events/set-accounts [account]]))
                         (when @active-account
                           (dispatch [::accounts-events/set-accounts []]))))
                       js/undefined)
                   (array account))
  (r/create-element ConnectWallet
                    #js {:className class
                         :theme (if (= @day-night "day") "light" "dark")
                         ;:switchToActiveChain true
                         :modalSize "wide"})))


(defn connect-wallet-btn [{:keys [:class] :as opts}]
    (r/create-element ThirdwebProvider
                      (clj->js {:activeChain (-> config-map
                                                 :web3-chain
                                                 (merge {:slug ""})
                                                 (update :chain-id int)
                                                 (clojure.set/rename-keys {:chain-id :chainId
                                                                           :rpc-urls :rpc
                                                                           :chain-name :name
                                                                           :native-currency :nativeCurrency})
                                                 clj->js)
                                :clientId "f478f4123340f16303e57df57b6e26ef"
                                :supportedWallets [(metamaskWallet #js {:recommended true})
                                                   (coinbaseWallet)
                                                   (walletConnect)
                                                   ;(rainbowWallet)
                                                   ;(trustWallet)
                                                   ]
                                :supportedChains []})
                      (r/as-element [:f> connect-wallet opts])))
