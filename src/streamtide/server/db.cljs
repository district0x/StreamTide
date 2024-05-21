(ns streamtide.server.db
  "Module for defining database structure and managing and abstracting queries to the database"
  (:require [cljs-web3-next.helpers :refer [zero-address]]
            [clojure.string :as string]
            [district.server.config :refer [config]]
            [district.server.db :as db]
            [district.server.db.column-types :refer [address default-nil default-zero default-false not-nil primary-key]]
            [honeysql-postgres.helpers :as psqlh]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlh]
            [mount.core :as mount :refer [defstate]]
            [streamtide.shared.utils :as shared-utils]
            [taoensso.timbre :as log]))

(declare start)
(declare stop)

(defstate ^{:on-reload :noop} streamtide-db
          :start (start (merge (:streamtide/db @config)
                               (:streamtide/db (mount/args))))
          :stop (stop))

; columns holding big numbers
(def big-numbers-fields [:matching-pool/amount
                         :matching-pool/distributed
                         :matching/amount
                         :donation/amount
                         :user/min-donation])

(defn- fix-exp-numbers [results]
  "when storing a big number, they may get converted to exponential notation.
  This makes sure all the numbers are expressed in the same plain format"
  (let [f (fn [result] (reduce (fn [result field]
                                 (if (get result field)
                                   (update result field shared-utils/safe-number-str)
                                   result))
                               result big-numbers-fields))]

    (if (seq? results)
      (mapv #(f %) results)
      (f results))))

(def db-get (fn [query]
              (-> (db/get query {:format-opts {:allow-namespaced-names? true}})
                  fix-exp-numbers)))
(def db-all (fn [query]
              (-> (db/all query {:format-opts {:allow-namespaced-names? true}})
                  fix-exp-numbers)))
(def db-run! #(db/run! %1 {:format-opts {:allow-namespaced-names? true}}))

;; DATABASE Schema

(def user-columns
  [[:user/address address primary-key not-nil]
   [:user/name :varchar default-nil]
   [:user/description :varchar default-nil]
   [:user/photo :varchar default-nil]
   [:user/bg-photo :varchar default-nil]
   [:user/tagline :varchar default-nil]
   [:user/handle :varchar default-nil]
   [:user/url :varchar default-nil]
   [:user/min-donation :unsigned :integer default-nil]
   [:user/creation-date :timestamp not-nil]
   [:user/blacklisted :tinyint default-false]])

(def social-link-columns
  [[:user/address address not-nil]
   [:social/network :varchar not-nil]
   [:social/url :varchar not-nil]
   [:social/verified :tinyint]
   [(sql/call :primary-key :user/address :social/network)]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def perks-columns
  [[:user/address address primary-key not-nil]
   [:user/perks :varchar default-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def notifications-category-columns
  [[:user/address address not-nil]
   [:notification/category :varchar not-nil]
   [:notification/type :varchar not-nil]
   [:notification/enable :tinyint]
   [(sql/call :primary-key :user/address :notification/category :notification/type)]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def notifications-type-columns
  [[:user/address address not-nil]
   [:notification/type :varchar not-nil]
   [:notification/user-id :varchar]
   [(sql/call :primary-key :user/address :notification/type)]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def notifications-type-many-columns
  [[:notification/id :integer primary-key :autoincrement]
   [:user/address address not-nil]
   [:notification/type :varchar not-nil]
   [:notification/user-id :varchar]
   [(sql/call :unique :user/address :notification/type :notification/user-id)]])

(def grant-columns
  [[:user/address address not-nil]
   [:grant/status :smallint not-nil]
   [:grant/request-date :timestamp not-nil]
   [:grant/decision-date :timestamp default-nil]
   [(sql/call :primary-key :user/address)]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def content-columns
  [[:content/id :integer primary-key :autoincrement]
   [:user/address address not-nil]
   [:content/public :tinyint not-nil]
   [:content/pinned :tinyint not-nil]
   [:content/creation-date :timestamp not-nil]
   [:content/type :varchar not-nil]
   [:content/url :varchar not-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def donation-columns
  [[:donation/id :integer primary-key :autoincrement]
   [:donation/sender address not-nil]
   [:donation/receiver address not-nil]
   [:donation/date :timestamp not-nil]
   [:donation/amount :unsigned :integer not-nil]  ;; TODO use string to avoid precision errors? order-by is important
   [:donation/coin address not-nil]
   [:round/id :unsigned :integer]
   [(sql/call :foreign-key :donation/sender) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :donation/receiver) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :donation/coin) (sql/call :references :coin :coin/address)]
   [(sql/call :foreign-key :round/id) (sql/call :references :round :round/id)]])

(def matching-columns
  [[:matching/id :integer primary-key :autoincrement]
   [:matching/receiver address not-nil]
   [:matching/date :timestamp not-nil]
   [:matching/amount :unsigned :integer not-nil]  ;; TODO use string to avoid precision errors? order-by is important
   [:matching/coin address not-nil]
   [:round/id :unsigned :integer]
   [(sql/call :foreign-key :matching/receiver) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :matching/coin) (sql/call :references :coin :coin/address)]
   [(sql/call :foreign-key :round/id) (sql/call :references :round :round/id)]])

(def user-roles-columns
  [[:role/id :integer primary-key :autoincrement]
   [:user/address address not-nil]
   [:role/role :string not-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :unique :user/address :role/role)]])

(def user-timestamp-columns
  [[:user/address address primary-key not-nil]
   [:timestamp/last-seen :timestamp :default-nil]
   [:timestamp/last-modification :timestamp :default-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def user-content-permission-columns
  [[:user/source-user address not-nil]
   [:user/target-user address not-nil]
   [(sql/call :primary-key :user/source-user :user/target-user)]
   [(sql/call :foreign-key :user/source-user) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :user/target-user) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def announcement-columns
  [[:announcement/id :integer primary-key :autoincrement]
   [:announcement/text :varchar not-nil]])

(def round-columns
  [[:round/id :integer primary-key not-nil]
   [:round/start :timestamp not-nil]
   [:round/duration :unsigned :integer not-nil]])

(def matching-pool-columns
  [[:round/id :unsigned :integer]
   [:matching-pool/coin :address not-nil]
   [:matching-pool/amount :unsigned :integer]   ;; TODO use string to avoid precision errors? order-by is important
   [:matching-pool/distributed :unsigned :integer]
   [(sql/call :primary-key :round/id :matching-pool/coin)]
   [(sql/call :foreign-key :matching-pool/coin) (sql/call :references :coin :coin/address)]
   [(sql/call :foreign-key :round/id) (sql/call :references :round :round/id)]])

(def coin-columns
  [[:coin/address address primary-key not-nil]
   [:coin/name :varchar default-nil]
   [:coin/symbol :varchar default-nil]
   [:coin/decimals :unsigned :integer default-nil]])

(def events-columns
  [[:event/contract-key :varchar not-nil]
   [:event/event-name :varchar not-nil]
   [:event/last-log-index :integer not-nil]
   [:event/last-block-number :integer not-nil]
   [:event/count :integer not-nil]
   [(sql/call :primary-key :event/contract-key :event/event-name)]])


(def user-column-names (filter keyword? (map first user-columns)))
(def social-link-column-names (filter keyword? (map first social-link-columns)))
(def perks-column-names (filter keyword? (map first perks-columns)))
(def notifications-category-column-names (filter keyword? (map first notifications-category-columns)))
(def notifications-type-column-names (filter keyword? (map first notifications-type-columns)))
(def notifications-type-many-column-names (filter keyword? (map first notifications-type-many-columns)))
(def grant-column-names (filter keyword? (map first grant-columns)))
(def content-column-names (filter keyword? (map first content-columns)))
(def donation-column-names (filter keyword? (map first donation-columns)))
(def matching-column-names (filter keyword? (map first matching-columns)))
(def user-roles-column-names (filter keyword? (map first user-roles-columns)))
(def user-timestamp-column-names (filter keyword? (map first user-timestamp-columns)))
(def user-content-permission-column-names (filter keyword? (map first user-content-permission-columns)))
(def announcement-column-names (filter keyword? (map first announcement-columns)))
(def round-column-names (filter keyword? (map first round-columns)))
(def matching-pool-column-names (filter keyword? (map first matching-pool-columns)))
(def coin-column-names (filter keyword? (map first coin-columns)))
(def events-column-names (filter keyword? (map first events-columns)))


;; Database functionality

(defn- paged-query
  "Execute a paged query.
  query: a map honeysql query.
  page-size: a int
  page-start-idx: a int
  Returns a map with [:items :total-count :end-cursor :has-next-page]"
  [query page-size page-start-idx]
  (let [paged-query (cond-> query
                            page-size (assoc :limit page-size)
                            page-start-idx (assoc :offset page-start-idx))
        total-count (count (db-all query))
        result (db-all paged-query)
        last-idx (cond-> (count result)
                         page-start-idx (+ page-start-idx))]
    (log/debug "Paged query result" result)
    {:items result
     :total-count total-count
     :end-cursor (str last-idx)
     :has-next-page (< last-idx total-count)}))

(defn get-user [user-address]
  (db-get {:select [:*]
           :from [:user]
           :where [:= user-address :user.user/address]}))

(defn get-user-socials [{:keys [:user/address :user/addresses :social/network :social/verified]}]
  (db-all (cond-> {:select [:*]
                   :from [:social-link]}
                  address (sqlh/merge-where [:= :social-link.user/address address])
                  addresses (sqlh/merge-where [:in :social-link.user/address addresses])
                  network (sqlh/merge-where [:= :social-link.social/network network])
                  (some? verified) (sqlh/merge-where [:= :social-link.social/verified verified]))))

(defn get-user-perks [current-user {:keys [:user/address]}]
  (db-get {:select [:p.*]
           :from [[:perks :p]]
           :join [[:user :u] [:= :p.user/address :u.user/address]]
           :where [:and
                   [:= :p.user/address address]
                   [:or
                    [:= :p.user/address current-user] ; ... or is the owner
                    [:exists {:select [1] :from [[:user-roles :ur]] :where [:and [:= :ur.user/address current-user] [:= :ur.role/role "admin"]]}] ; ... or is an admin
                    [:in address {:select [:ucp.user/target-user] :from [[:user-content-permissions :ucp]] :where [:= :ucp.user/source-user current-user]}] ; ... or has explicit permission to it]
                   ]
                   [:= :u.user/blacklisted false]]}))


(defn get-notification-categories [{:keys [:user/address :user/addresses :notification/category :notification/type :notification/enable]}]
  (db-all (cond-> {:select [:nc.*]
                   :from [[:notification-category :nc]]
                   :join [[:user :u] [:= :nc.user/address :u.user/address]]
                   :where [:= :u.user/blacklisted false]}
                  address (sqlh/merge-where [:= :nc.user/address address])
                  addresses (sqlh/merge-where [:in :nc.user/address addresses])
                  category (sqlh/merge-where [:= :nc.notification/category category])
                  type (sqlh/merge-where [:= :nc.notification/type type])
                  (some? enable) (sqlh/merge-where [:= :nc.notification/enable enable]))))

(defn get-notification-types [{:keys [:user/address :user/addresses :notification/type :notification/user-id]}]
  (db-all (cond-> {:select [:*]
                   :from [:notification-type]}
                  address (sqlh/merge-where [:= :user/address address])
                  addresses (sqlh/merge-where [:in :user/address addresses])
                  type (sqlh/merge-where [:= :notification/type type])
                  user-id (sqlh/merge-where [:= :notification/user-id user-id]))))

(defn get-notification-types-many [{:keys [:user/address :user/addresses :notification/type :notification/user-id]}]
  (db-all (cond-> {:select [:*]
                   :from [:notification-type-many]}
                  address (sqlh/merge-where [:= :user/address address])
                  addresses (sqlh/merge-where [:in :user/address addresses])
                  type (sqlh/merge-where [:= :notification/type type])
                  user-id (sqlh/merge-where [:= :notification/user-id user-id]))))

(defn get-grant [user-address]
  (db-get {:select [:*]
           :from [:grant]
           :join [:user [:= :grant.user/address :user.user/address]]
           :where [:= user-address :user.user/address]}))

(defn get-grants [{:keys [:statuses :search-term :order-by :order-dir :first :after] :as args}]
  (let [statuses-set (when statuses (set statuses))
        page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:grant.* :user.*]
                 :from [:grant]
                 :join [:user [:= :grant.user/address :user.user/address]]
                 :where [:!= :user/blacklisted true]}
                statuses-set (sqlh/merge-where [:in :grant.grant/status statuses-set])
                search-term  (sqlh/merge-where [:like :user.user/name (str "%" search-term "%")])
                order-by (sqlh/merge-order-by [[(get {:grants.order-by/request-date :grant.grant/request-date
                                                      :grants.order-by/decision-date :grant.grant/decision-date
                                                      :grants.order-by/username [:user.user/name [:collate :nocase]]}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-users [{:keys [:user/name :user/address :user/blacklisted :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:*]
                 :from [:user]}
                (or
                  (= order-by :users.order-by/last-seen)
                  (= order-by :users.order-by/last-modification))
                (sqlh/merge-left-join
                  :user-timestamp [:= :user.user/address :user-timestamp.user/address])
                (some? blacklisted) (sqlh/merge-where [:= :user/blacklisted blacklisted])
                name (sqlh/merge-where [:like :user.user/name (str "%" name "%")])
                address (sqlh/merge-where [:like :user.user/address (str "%" address "%")])
                search-term (sqlh/merge-where [:or
                                               [:like :user.user/name (str "%" search-term "%")]
                                               [:like :user.user/address (str "%" search-term "%")]])
                order-by (sqlh/merge-order-by [[(get {:users.order-by/address [:user.user/address [:collate :nocase]]
                                                      :users.order-by/username [:user.user/name [:collate :nocase]]
                                                      :users.order-by/creation-date :user.user/creation-date
                                                      :users.order-by/last-seen :user-timestamp.timestamp/last-seen
                                                      :users.order-by/last-modification :user-timestamp.timestamp/last-modification}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-announcements [{:keys [:first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query {:select [:*]
               :from [:announcement]
               :order-by [[:announcement.announcement/id :desc]]}]
    (paged-query query page-size page-start-idx)))

(defn get-contents [current-user {:keys [:user/address :only-public :content/pinned :order-by :order-dir :first :after] :as args}]
    (let [page-start-idx (when after (js/parseInt after))
          page-size first
          query (cond->
                  {:select [:c.* :u.*]
                   :from [[:content :c]]
                   :join [[:user :u] [:= :c.user/address :u.user/address]]
                   :where [:and
                           [:or
                            [:= :c.content/public 1] ; get content if is public ...
                            [:= :u.user/address current-user] ; ... or is the owner
                            [:exists {:select [1] :from [[:user-roles :ur]] :where [:and [:= :ur.user/address current-user] [:= :ur.role/role "admin"]]}] ; ... or is an admin
                            [:in address {:select [:ucp.user/target-user] :from [[:user-content-permissions :ucp]] :where [:= :ucp.user/source-user current-user]}] ; ... or has explicit permission to it
                           ]
                           [:= :user/blacklisted false]]
                   }
                  address (sqlh/merge-where [:= :u.user/address address])
                  only-public (sqlh/merge-where [:= :c.content/public 1])
                  (some? pinned) (sqlh/merge-where [:= :c.content/pinned pinned])
                  order-by (sqlh/merge-order-by [[(get {:contents.order-by/creation-date :c.content/creation-date}
                                                       order-by)
                                                  (or (keyword order-dir) :asc)]]))]
      (paged-query query page-size page-start-idx)))

(defn get-donations [{:keys [:sender :receiver :round :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:d.* :u.*]
                 :from [[:donation :d]]
                 :join [[:user :u] [:= :d.donation/receiver :u.user/address]]}
                search-term (sqlh/merge-where [:like :u.user/name (str "%" search-term "%")])
                sender (sqlh/merge-where [:= :d.donation/sender sender])
                receiver (sqlh/merge-where [:= :d.donation/receiver receiver])
                round (sqlh/merge-where [:= :d.round/id round])
                order-by (sqlh/merge-order-by [[(get {:donations.order-by/date :d.donation/date
                                                      :donations.order-by/username [:u.user/name [:collate :nocase]]
                                                      :donations.order-by/amount :d.donation/amount}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-matchings [{:keys [:receiver :round :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:m.* :u.*]
                 :from [[:matching :m]]
                 :join [[:user :u] [:= :m.matching/receiver :u.user/address]]}
                search-term (sqlh/merge-where [:like :u.user/name (str "%" search-term "%")])
                receiver (sqlh/merge-where [:= :m.matching/receiver receiver])
                round (sqlh/merge-where [:= :m.round/id round])
                order-by (sqlh/merge-order-by [[(get {:matchings.order-by/date :m.matching/date
                                                      :matchings.order-by/username [:u.user/name [:collate :nocase]]
                                                      :matchings.order-by/amount :m.matching/amount}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn group-leaders [leaders]
  (vals (reduce
          (fn [ret x]
            (let [k (:user/address x)]
              (assoc ret k (merge (select-keys x user-column-names)
                                  (let [da (get-in ret [k :leader/donation-amount] 0)
                                        ma (get-in ret [k :leader/matching-amounts] [])
                                        ta (get-in ret [k :leader/total-amounts] [])]
                                    {:leader/donation-amount (if (:leader/donation-amount x)
                                                               (:leader/donation-amount x)
                                                               da)
                                     :leader/matching-amounts (if (:matching/coin x)
                                                             (conj ma (clojure.set/rename-keys (select-keys x [:matching/coin :leader/matching-amount])
                                                                                               {:matching/coin :coin :leader/matching-amount :amount}))
                                                             ma)
                                     :leader/total-amounts (if (:matching/coin x)
                                                             (conj ta (clojure.set/rename-keys (select-keys x [:matching/coin :leader/total-amount])
                                                                                               {:matching/coin :coin :leader/total-amount :amount}))
                                                             ta)})))))
          {} leaders)))

(defn get-leaders [{:keys [:round :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        sub-query-donations (cond-> {:select [:donation/receiver [(sql/call :sum :donation/amount) :donations]]
                                     :from [:donation] :group-by [:donation/receiver]}
                                    round (sqlh/merge-where [:= :donation.round/id round]))
        sub-query-matchings (cond-> {:select [:matching/receiver [(sql/call :sum :matching/amount) :matchings]]
                                     :from [:matching] :where [:= :matching/coin zero-address] :group-by [:matching/receiver]}
                                    round (sqlh/merge-where [:= :matching.round/id round]))
        query (cond->
                {:select [:u.*
                          [zero-address :matching/coin]
                          [(sql/call :coalesce :donations 0) :leader/donation-amount]
                          [(sql/call :coalesce :matchings 0) :leader/matching-amount]
                          [(sql/call :+ (sql/call :coalesce :donations 0) (sql/call :coalesce :matchings 0))
                           :leader/total-amount]]
                 :from [[:user :u]]
                 :left-join [[sub-query-donations :d]
                             [:= :d.donation/receiver :u.user/address]
                             [sub-query-matchings :m]
                             [:= :m.matching/receiver :u.user/address]]
                 :where [:and [:> :leader/total-amount 0]
                         [:= :user/blacklisted false]]}
                search-term (sqlh/merge-where [:like :u.user/name (str "%" search-term "%")])
                order-by (sqlh/merge-order-by [[(get {:leaders.order-by/username [:u.user/name [:collate :nocase]]
                                                      :leaders.order-by/donation-amount :leader/donation-amount
                                                      :leaders.order-by/matching-amount :leader/matching-amount
                                                      :leaders.order-by/total-amount :leader/total-amount}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))
        leaders (paged-query query page-size page-start-idx)
        other-coins-query {:select [:u.*
                                    :m.matching/coin
                                    [(sql/call :sum :m.matching/amount) :leader/matching-amount]
                                    [(sql/call :sum :m.matching/amount) :leader/total-amount]]
                           :from [[:user :u]]
                           :join [[:matching :m] [:= :m.matching/receiver :u.user/address]]
                           :where [:and [:in :u.user/address (map :user/address (:items leaders))]
                                   [:!= :m.matching/coin zero-address]]
                           :group-by [:m.matching/receiver :m.matching/coin]}
        leaders (update leaders :items #(concat % (db-all other-coins-query)))]
    (update leaders :items group-leaders)))

(defn group-rounds [rounds]
  (vals (reduce
          (fn [ret x]
            (let [k (:round/id x)]
              (assoc ret k (merge
                             (get ret k)
                             (select-keys x [:round/id :round/start :round/duration])
                             (let [mp (get-in ret [k :round/matching-pools] [])]
                               {:round/matching-pools (if (:matching-pool/coin x)
                                                        (conj mp (select-keys x [:matching-pool/coin :matching-pool/amount :matching-pool/distributed]))
                                                        mp)})))))
          {} rounds)))

(defn get-round [round-id]
  (let [rounds (db-all {:select [:r.* :mp.matching-pool/coin
                                 [(sql/call :coalesce :mp.matching-pool/amount 0) :matching-pool/amount]
                                 [(sql/call :coalesce :mp.matching-pool/distributed 0) :matching-pool/distributed]]
           :from [[:round :r]]
           :left-join [[:matching-pool :mp] [:= :r.round/id :mp.round/id]]
           :where [:= round-id :r.round/id]})]
    (first (group-rounds rounds))))

(defn get-rounds [{:keys [:order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        round-query (cond->
                {:select [:*]
                 :from [[:round :r]]}
                order-by (sqlh/merge-order-by [[(get {:rounds.order-by/date :r.round/start
                                                      :rounds.order-by/id :r.round/id}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))
        rounds (paged-query round-query page-size page-start-idx)
        mp-query {:select [:*]
                  :from [[:matching-pool :mp]]
                  :where [:in :mp.round/id (map #(:round/id %) (:items rounds))]}
        mps (db-all mp-query)]
    (update rounds :items #(group-rounds (apply merge % mps)))))

(defn get-matching-pool [round-id coin-address]
  (db-get {:select [:*]
           :from [[:matching-pool :mp]]
           :where [:and
                   [:= round-id :mp.round/id]
                   [:= coin-address :mp.matching-pool/coin]]}))

(defn get-coin [address]
  (db-get {:select [:*]
           :from [:coin]
           :where [:= (string/lower-case address) :coin.coin/address]}))

(defn get-user-timestamps [{:keys [:user/address]}]
  (db-get {:select [:*]
           :from [:user-timestamp]
           :where [:= address :user-timestamp.user/address]}))

(defn get-user-content-permissions [{:keys [:user/source-user :user/target-user]}]
  (db-all (cond->
            {:select [:*]
             :from [[:user-content-permissions :ucp]]}
            source-user (sqlh/merge-where [:= :ucp.user/source-user source-user])
            target-user (sqlh/merge-where [:= :ucp.user/target-user target-user]))))

(defn has-private-content? [{:keys [:user/address]}]
  (not (empty? (db-get {:select [1]
           :from [[:content :c]]
           :where [:and
                   [:= :c.user/address address]
                   [:= :c.content/public false]]
           :limit 1}))))

(defn has-permission? [{:keys [:user/source-user :user/target-user]}]
  (not (empty? (db-get {:select [1]
           :from [[:user :u]]
           :where [:and
                   [:= :u.user/address target-user]
                   [:or
                    [:= :u.user/address source-user] ; ... or is the owner
                    [:exists {:select [1] :from [[:user-roles :ur]] :where [:and [:= :ur.user/address source-user] [:= :ur.role/role "admin"]]}] ; ... or is an admin
                    [:in target-user {:select [:ucp.user/target-user] :from [[:user-content-permissions :ucp]] :where [:= :ucp.user/source-user source-user]}] ; ... or has explicit permission to it
                    ]
                   [:= :u.user/blacklisted false]]
           :limit 1}))))

(defn get-roles [user-address]
  (db-all {:select [:user-roles.role/role]
           :from [:user-roles]
           :where [:= user-address :user-roles.user/address]}))

(defn add-role [user-address role]
  (db-run! {:insert-into :user-roles
            :values [{:user/address user-address
                      :role/role (name role)}]}))

(defn remove-role [user-address role]
  (db-run! {:delete-from :user-roles
            :where [:and [:= :user/address user-address] [:= :role/role (name role)]]}))

(defn upsert-user-info! [args]
  (let [user-info (select-keys args user-column-names)]
    (db-run! {:insert-into :user
              :values [(merge {:user/creation-date (shared-utils/now-secs)}
                              user-info)]
              :upsert {:on-conflict [:user/address]
                       :do-update-set (remove #{:user/creation-date} (keys user-info))}})))

(defn ensure-users-exist! [addresses]
  (log/debug "ensure-users-exist!" {:addresses addresses})
  (let [user-infos (map (fn [address]
                          {:user/address address
                           :user/creation-date (shared-utils/now-secs)})
                        addresses)]
    (db-run! {:insert-into :user
              :values user-infos
              :upsert {:on-conflict [:user/address]
                       :do-nothing []}})))

(defn upsert-user-socials! [social-links]
  (log/debug "user-social" {:social-links social-links})
  (db-run! {:insert-into :social-link
            :values (mapv #(select-keys % social-link-column-names) social-links)
            :upsert {:on-conflict [:user/address :social/network]
                     :do-update-set (keys (select-keys (first social-links) social-link-column-names))}}))

(defn remove-user-socials! [{:keys [:user/address :social/networks :social/url] :as args}]
  (log/debug "remove-user-socials" args)
  (when (not-empty (select-keys args [:user/address :social/url]))
    (db-run! (cond-> {:delete-from :social-link}
                     address (sqlh/merge-where [:= :user/address address])
                     networks (sqlh/merge-where [:in :social/network networks])
                     url (sqlh/merge-where [:= :social/url url])))))

(defn upsert-user-perks! [{:keys [:user/address :user/perks] :as args}]
  (log/debug "upsert-user-perks!" args)
  (db-run! {:insert-into :perks
            :values [(select-keys args perks-column-names)]
            :upsert {:on-conflict [:user/address]
                     :do-update-set (keys (select-keys args perks-column-names))}}))

(defn remove-user-perks! [{:keys [:user/address] :as args}]
  (log/debug "remove-user-perks" args)
  (when (not-empty (select-keys args [:user/address]))
    (db-run! {:delete-from :perks
              :where [:= :user/address address]})))

(defn upsert-notification-categories! [categories-setting]
  (log/debug "add-to-notification-category!" {:args categories-setting})
  (db-run! {:insert-into :notification-category
            :values (map #(select-keys % notifications-category-column-names) categories-setting)
            :upsert {:on-conflict [:user/address :notification/category :notification/type]
                     :do-update-set [:notification/enable]}}))

(defn remove-from-notification-category! [{:keys [:user/address :notification/category :notification/type] :as args}]
  (log/debug "remove-from-notification-category!" args)
  (when address
    (db-run! (cond-> {:delete-from :notification-category
                      :where [:= :user/address address]}
                     category (sqlh/merge-where [:= :notification/category category])
                     type (sqlh/merge-where [:= :notification/type type])))))

(defn upsert-notification-type! [{:keys [:user/address :notification/type :notification/user-id] :as args}]
  (log/debug "upsert-notification-type!" args)
  (db-run! {:insert-into :notification-type
            :values [(select-keys args notifications-type-column-names)]
            :upsert {:on-conflict [:user/address :notification/type]
                     :do-update-set [:notification/user-id]}}))

(defn remove-notification-type! [{:keys [:user/address :notification/type :notification/user-id] :as args}]
  (log/debug "remove-notification-type!" args)
  (when (and address type)
    (db-run! (cond-> {:delete-from :notification-type}
                     address (sqlh/merge-where [:= :user/address address])
                     type (sqlh/merge-where [:= :notification/type type])
                     user-id (sqlh/merge-where [:= :notification/user-id user-id])))))

(defn add-notification-type-many! [{:keys [:user/address :notification/type :notification/user-id] :as args}]
  (log/debug "add-notification-type-many!" args)
  (db-run! {:insert-into :notification-type-many
            :values [(select-keys args notifications-type-many-column-names)]
            :upsert {:on-conflict [:user/address :notification/type :notification/user-id]
                     :do-nothing []}}))

(defn remove-notification-type-many! [{:keys [:user/address :notification/type :notification/id :notification/user-id] :as args}]
  (log/debug "remove-notification-type-many!" args)
  (when (or (and address type) id)
    (db-run! (cond-> {:delete-from :notification-type-many}
                     address (sqlh/merge-where [:= :user/address address])
                     type (sqlh/merge-where [:= :notification/type type])
                     id (sqlh/merge-where [:= :notification/id id])
                     user-id (sqlh/merge-where [:= :notification/user-id user-id])))))

(defn upsert-grants! [{:keys [:user/addresses :grant/status :grant/decision-date] :as args}]
  "Insert new grants for given users or update them if the users already requested a grant"
  (log/debug "insert-grants" args)
  (db-run! {:insert-into :grant
            :values (map (fn [address]
                           {:user/address address
                            :grant/status status
                            :grant/decision-date decision-date
                            :grant/request-date (or decision-date (shared-utils/now-secs))}) addresses)
            :upsert {:on-conflict [:user/address]
                     :do-update-set [:grant/status :grant/decision-date]}}))

(defn add-donation! [args]
  (log/debug "add-donation" args)
  (db-run! {:insert-into :donation
            :values [(select-keys args donation-column-names)]}))

(defn add-matching! [args]
  (log/debug "add-matching" args)
  (db-run! {:insert-into :matching
            :values [(select-keys args matching-column-names)]}))

(defn add-round! [args]
  (log/debug "add-round" args)
  (db-run! {:insert-into :round
            :values [(select-keys args round-column-names)]}))

(defn update-round! [{:keys [:round/id] :as args}]
  (log/debug "update-round" args)
  (let [round-info (select-keys args (remove :round/id round-column-names))]
    (db-run! {:update :round
              :set round-info
              :where [:= :round/id id]})))

(defn upsert-matching-pool! [{:keys [:round/id :matching-pool/coin] :as args}]
  (log/debug "update-matching-pool" args)
  (let [matching-pool (select-keys args matching-pool-column-names)]
    (db-run! {:insert-into :matching-pool
              :values [matching-pool]
              :upsert {:on-conflict [:round/id :matching-pool/coin]
                       :do-update-set (keys matching-pool)}})))

(defn add-coin! [args]
  (log/debug "add-coin" args)
  (db-run! {:insert-into :coin
            :values [(update (select-keys args coin-column-names) :coin/address string/lower-case)]
            :upsert {:on-conflict [:coin/address]
                     :do-nothing []}}))

(defn blacklisted? [{:keys [:user/address] :as args}]
  (let [bl (:user/blacklisted (db-get {:select [:user/blacklisted]
                                      :from [:user]
                                      :where [:= :user/address address]}))]
    (not (or (nil? bl) (zero? bl)))))

(defn add-to-blacklist! [{:keys [:user/address]}]
  (db-run! {:update :user
            :set {:user/blacklisted true}
            :where [:= :user/address address]}))

(defn remove-from-blacklist! [{:keys [:user/address]}]
  (db-run! {:update :user
            :set {:user/blacklisted false}
            :where [:= :user/address address]}))


(defn add-announcement! [{:keys [:announcement/text]}]
  (db-run! {:insert-into :announcement
            :values [{:announcement/text text}]}))


(defn remove-announcement! [{:keys [:announcement/id]}]
  (db-run! {:delete-from :announcement
            :where [:= :announcement/id id]}))


(defn add-content! [args]
  (db-run! {:insert-into :content
            :values [(merge {:content/creation-date (shared-utils/now-secs)}
                           (select-keys args content-column-names))]}))


(defn remove-content! [{:keys [:content/id :user/address]}]
  (db-run! {:delete-from :content
            :where [:and
                    [:= :content/id id]
                    [:= :user/address address]]}))


(defn set-content-visibility! [{:keys [:content/id :content/public :user/address]}]
  (db-run! {:update :content
            :set {:content/public public}
            :where [:and
                    [:= :content/id id]
                    [:= :user/address address]]}))


(defn set-content-pinned! [{:keys [:content/id :content/pinned :user/address]}]
  (db-run! {:update :content
            :set {:content/pinned pinned}
            :where [:and
                    [:= :content/id id]
                    [:= :user/address address]]}))


(defn add-user-content-permission! [{:keys [:user/source-user :user/target-user] :as args}]
  (db-run! {:insert-into :user-content-permissions
            :values [(select-keys args user-content-permission-column-names)]
            :upsert {:on-conflict [:user/source-user :user/target-user]
                     :do-nothing []}}))

(defn set-user-timestamp! [{:keys [:user-addresses :timestamp/last-seen :timestamp/last-modification] :as args}]
  (db-run! {:insert-into :user-timestamp
            :values (map (fn [address]
                           (merge
                             (select-keys args user-timestamp-column-names)
                             {:user/address address})) user-addresses)
            :upsert {:on-conflict [:user/address]
                     :do-update-set (remove #{:user-addresses} (keys args))}}))

(defn all-events []
  (db-all {:select [:*]
           :from [:events]}))

(defn get-last-event [contract-key event-name]
  (db-get {:select [:event/last-log-index :event/last-block-number :event/count]
           :from [:events]
           :where [:and
                   [:= :event/contract-key contract-key]
                   [:= :event/event-name event-name]]}))

(defn upsert-event! [args]
  (db-run! {:insert-into :events
            :values [(select-keys args events-column-names)]
            :upsert {:on-conflict [:event/event-name :event/contract-key]
                     :do-update-set [:event/last-log-index :event/last-block-number :event/count]}}))



(defn clean-db []
  (let [tables []
        drop-table-if-exists (fn [t]
                               (psqlh/drop-table :if-exists t))]
    (doall
      (map (fn [t]
             (log/debug (str "Dropping table " t))
             (db-run! (drop-table-if-exists t)))
           tables))))


(defn create-tables []
  (db-run! (-> (psqlh/create-table :user :if-not-exists)
               (psqlh/with-columns user-columns)))

  (db-run! (-> (psqlh/create-table :social-link :if-not-exists)
               (psqlh/with-columns social-link-columns)))

  (db-run! (-> (psqlh/create-table :perks :if-not-exists)
               (psqlh/with-columns perks-columns)))

  (db-run! (-> (psqlh/create-table :notification-category :if-not-exists)
               (psqlh/with-columns notifications-category-columns)))

  (db-run! (-> (psqlh/create-table :notification-type :if-not-exists)
               (psqlh/with-columns notifications-type-columns)))

  (db-run! (-> (psqlh/create-table :notification-type-many :if-not-exists)
               (psqlh/with-columns notifications-type-many-columns)))

  (db-run! (-> (psqlh/create-table :grant :if-not-exists)
               (psqlh/with-columns grant-columns)))

  (db-run! (-> (psqlh/create-table :content :if-not-exists)
               (psqlh/with-columns content-columns)))

  (db-run! (-> (psqlh/create-table :donation :if-not-exists)
               (psqlh/with-columns donation-columns)))

  (db-run! (-> (psqlh/create-table :matching :if-not-exists)
               (psqlh/with-columns matching-columns)))

  (db-run! (-> (psqlh/create-table :user-roles :if-not-exists)
               (psqlh/with-columns user-roles-columns)))

  (db-run! (-> (psqlh/create-table :user-timestamp :if-not-exists)
               (psqlh/with-columns user-timestamp-columns)))

  (db-run! (-> (psqlh/create-table :user-content-permissions :if-not-exists)
               (psqlh/with-columns user-content-permission-columns)))

  (db-run! (-> (psqlh/create-table :announcement :if-not-exists)
               (psqlh/with-columns announcement-columns)))

  (db-run! (-> (psqlh/create-table :round :if-not-exists)
               (psqlh/with-columns round-columns)))

  (db-run! (-> (psqlh/create-table :matching-pool :if-not-exists)
               (psqlh/with-columns matching-pool-columns)))

  (db-run! (-> (psqlh/create-table :coin :if-not-exists)
               (psqlh/with-columns coin-columns)))

  (add-coin! {:coin/address zero-address
                       :coin/decimals 18
                       :coin/symbol "ETH"
                       :coin/name "Ether"})

  (db-run! (-> (psqlh/create-table :events :if-not-exists)
               (psqlh/with-columns events-columns))))


(defn start [args]
  (create-tables)

  ::started)

(defn stop []
  ::stopped)
