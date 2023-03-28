(ns streamtide.server.db
  "Module for defining database structure and managing and abstracting queries to the database"
  (:require [district.server.config :refer [config]]
            [district.server.db :as db]
            [district.server.db.column-types :refer [address default-nil default-zero not-nil primary-key]]
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

(def db-get #(db/get %1 {:format-opts {:allow-namespaced-names? true}}))
(def db-all #(db/all %1 {:format-opts {:allow-namespaced-names? true}}))
(def db-run! #(db/run! %1 {:format-opts {:allow-namespaced-names? true}}))

;; DATABASE Schema

(def user-columns
  [[:user/address address primary-key]
   [:user/name :varchar not-nil]
   [:user/description :varchar default-nil]
   [:user/photo :varchar default-nil]
   [:user/bg-photo :varchar default-nil]
   [:user/tagline :varchar default-nil]
   [:user/handle :varchar default-nil]
   [:user/url :varchar default-nil]
   [:user/perks :varchar default-nil]])

(def social-link-columns
  [[:user/address address not-nil]
   [:social/network :varchar not-nil]
   [:social/url :varchar not-nil]
   [:social/verified :tinyint]
   [(sql/call :primary-key :user/address :social/network)]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

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
   [:donation/matching :unsigned :integer not-nil]
   [(sql/call :foreign-key :donation/receiver) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def user-roles-columns
  [[:role/id :integer primary-key :autoincrement]
   [:user/address address not-nil]
   [:role/role :string not-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def user-content-permission-columns
  [[:user/source-user address not-nil]
   [:user/target-user address not-nil]
   [(sql/call :primary-key :user/source-user :user/target-user)]
   [(sql/call :foreign-key :user/source-user) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :user/target-user) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]])

(def blacklist-columns
  [[:user/address address primary-key]
   [:blacklisted/date :timestamp]
   ;; TODO can we only ban actual users?
   ;[(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
  ])

(def announcement-columns
  [[:announcement/id :integer primary-key :autoincrement]
   [:announcement/text :varchar not-nil]])


(def user-column-names (filter keyword? (map first user-columns)))
(def social-link-column-names (filter keyword? (map first social-link-columns)))
(def grant-column-names (filter keyword? (map first grant-columns)))
(def content-column-names (filter keyword? (map first content-columns)))
(def donation-column-names (filter keyword? (map first donation-columns)))
(def user-roles-column-names (filter keyword? (map first user-roles-columns)))
(def user-content-permission-names (filter keyword? (map first user-content-permission-columns)))
(def blacklist-names (filter keyword? (map first blacklist-columns)))
(def announcement-names (filter keyword? (map first announcement-columns)))


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

(defn get-user-socials [user-address]
  (let [sql-query (db-all {:select [:*]
                           :from [:social-link]
                           :where [:= user-address :social-link.user/address]})]
    sql-query))

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
                 :join [:user [:= :grant.user/address :user.user/address]]}
                statuses-set (sqlh/merge-where [:in :grant.grant/status statuses-set])
                search-term  (sqlh/merge-where [:like :user.user/name (str "%" search-term "%")])
                order-by (sqlh/merge-order-by [[(get {:grants.order-by/request-date :grant.grant/request-date
                                                      :grants.order-by/decision-date :grant.grant/decision-date
                                                      :grants.order-by/username [:user.user/name [:collate :nocase]]}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-users [{:keys [:user/name :user/address :user/blacklisted :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:user.* [(sql/call :iif :blacklist.user/address true false) :user/blacklisted]]
                 :from [:user]
                 :left-join [:blacklist [:= :user.user/address :blacklist.user/address]]}
                (some? blacklisted) (sqlh/merge-where [(if blacklisted :!= :=) :blacklist.user/address nil])
                name (sqlh/merge-where [:like :user.user/name (str "%" name "%")])
                address (sqlh/merge-where [:like :user.user/address (str "%" address "%")])
                order-by (sqlh/merge-order-by [[(get {:users.order-by/address [:user.user/address [:collate :nocase]]
                                                      :users.order-by/username [:user.user/name [:collate :nocase]]}
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

(defn get-contents [current-user {:keys [:user/address :only-public :order-by :order-dir :first :after] :as args}]
    (let [page-start-idx (when after (js/parseInt after))
          page-size first
          query (cond->
                  {:select [:c.* :u.*]
                   :from [[:content :c]]
                   :join [[:user :u] [:= :c.user/address :u.user/address]]
                   :where [:or
                           [:= :c.content/public 1] ; get content if is public ...
                           [:= :u.user/address current-user] ; ... or is the owner
                           [:exists {:select [1] :from [[:user-roles :ur]] :where [:and [:= :ur.user/address current-user] [:= :ur.role/role "admin"]]}] ; ... or is an admin
                           [:in current-user {:select [:ucp.user/target-user] :from [[:user-content-permissions :ucp]] :where [:= :ucp.user/source-user current-user]}] ; ... or has explicit permission to it
                           ]
                   }
                  address (sqlh/merge-where [:= :u.user/address address])
                  only-public (sqlh/merge-where [:= :c.content/public 1])
                  order-by (sqlh/merge-order-by [[(get {:contents.order-by/creation-date :c.content/creation-date}
                                                       order-by)
                                                  (or (keyword order-dir) :asc)]]))]
      (paged-query query page-size page-start-idx)))

(defn get-donations [{:keys [:sender :receiver :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                ;                  {:select [:d.*
                ;                            [:sender.user/address :donation.sender/address]
                ;                            [:sender.user/photo :donation.sender/photo]
                ;                            [:receiver.user/address :donation.receiver/address]
                ;                            [:receiver.user/photo :donation.receiver/photo]
                ;                            ...
                ;                            ]
                {:select [:d.* :u.* [(sql/call :+ :d.donation/amount :d.donation/matching) :total-amount]]
                 :from [[:donation :d]]
                 :join [[:user :u] [:= :d.donation/receiver :u.user/address]]}
                search-term (sqlh/merge-where [:like :u.user/name (str "%" search-term "%")])
                sender (sqlh/merge-where [:= :d.donation/sender sender])
                receiver (sqlh/merge-where [:= :d.donation/receiver receiver])
                order-by (sqlh/merge-order-by [[(get {:donations.order-by/date :d.donation/date
                                                      :donations.order-by/username [:u.user/name [:collate :nocase]]
                                                      :donations.order-by/granted-amount :d.donation/amount
                                                      :donations.order-by/matching-amount :d.donation/matching
                                                      :donations.order-by/total-amount :total-amount}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

;(defn get-blacklisted [{:keys [:sender :receiver :search-term :order-by :order-dir :first :after] :as args}]
;  (let [page-start-idx (when after (js/parseInt after))
;        page-size first
;        query (cond->
;                {:select [:*]
;                 :from [[:blacklist :b]]
;                 :left-join [[:user :u] [:= :b.user/address :u.user/address]]}
;                search-term (sqlh/merge-where [:or
;                                               [:like :u.user/name (str "%" search-term "%")]
;                                               [:like :b.user/address (str "%" search-term "%")]])
;                order-by (sqlh/merge-order-by [[(get {:blacklisted.order-by/address :b.user/address
;                                                      :blacklisted.order-by/username [:u.user/name [:collate :nocase]]
;                                                      :blacklisted.order-by/blacklisted-date :b.blacklisted/date}
;                                                     order-by)
;                                                (or (keyword order-dir) :asc)]]))]
;    (paged-query query page-size page-start-idx)))

(defn get-roles [user-address]
  (db-all {:select [:user-roles.role/role]
           :from [:user-roles]
           :where [:= user-address :user-roles.user/address]}))

(defn upsert-user-info! [args]
  (let [user-info (select-keys args user-column-names)]
    (db-run! {:insert-into :user
              :values [user-info]
              :upsert {:on-conflict [:user/address]
                       :do-update-set (keys user-info)}})))

(defn upsert-user-socials! [social-links]
  (log/debug "user-social" {:social-links social-links})
  (db-run! {:insert-into :social-link
            :values (mapv #(select-keys % social-link-column-names) social-links)
            :upsert {:on-conflict [:user/address :social/network]
                     :do-update-set (keys (select-keys (first social-links) social-link-column-names))}}))

(defn remove-user-socials! [user-address social-networks]
  (db-run! {:delete-from :social-link
            :where [:and [:= :user/address user-address] [:in :social/network social-networks]]}))

(defn insert-grant! [{:keys [:user/address :grant/status] :as args}]
  "Insert a new grant for a user. Does nothing if the user already requested a grant"
  (log/debug "insert-grant" args)
  (db-run! {:insert-into :grant
            :values [{:user/address address
                      :grant/status status
                      :grant/request-date (shared-utils/now)}]
            :upsert {:on-conflict [:user/address]
                     :do-nothing []}}))

(defn update-grant! [{:keys [:user/address] :as args}]
  (log/debug "update-grant" args)
  (let [grant-info (select-keys args (remove :user/address grant-column-names))]
    (db-run! {:update :grant
              :set grant-info
              :where [:= :user/address address]})))

(defn blacklisted? [{:keys [:user/address] :as args}]
  (boolean (seq (db-get {:select [:*]
                    :from [:blacklist]
                    :where [:= :user/address address]
                    :limit 1}))))

(defn add-to-blacklist! [{:keys [:user/address]}]
  (db-run! {:insert-into :blacklist
           :values [{:user/address address
                     :blacklisted/date (shared-utils/now)}]
            :upsert {:on-conflict [:user/address]
                     :do-nothing []}}))

(defn remove-from-blacklist! [{:keys [:user/address]}]
  (db-run! {:delete-from :blacklist
            :where [:= :user/address address]}))


(defn add-announcement! [{:keys [:announcement/text]}]
  (db-run! {:insert-into :announcement
            :values [{:announcement/text text}]}))


(defn remove-announcement! [{:keys [:announcement/id]}]
  (db-run! {:delete-from :announcement
            :where [:= :announcement/id id]}))


(defn add-content! [args]
  (db-run! {:insert-into :content
            :values [(merge {:content/creation-date (shared-utils/now)}
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

  (db-run! (-> (psqlh/create-table :grant :if-not-exists)
               (psqlh/with-columns grant-columns)))

  (db-run! (-> (psqlh/create-table :content :if-not-exists)
               (psqlh/with-columns content-columns)))

  (db-run! (-> (psqlh/create-table :donation :if-not-exists)
               (psqlh/with-columns donation-columns)))

  (db-run! (-> (psqlh/create-table :user-roles :if-not-exists)
               (psqlh/with-columns user-roles-columns)))

  (db-run! (-> (psqlh/create-table :user-content-permissions :if-not-exists)
               (psqlh/with-columns user-content-permission-columns)))

  (db-run! (-> (psqlh/create-table :blacklist :if-not-exists)
               (psqlh/with-columns blacklist-columns)))

  (db-run! (-> (psqlh/create-table :announcement :if-not-exists)
               (psqlh/with-columns announcement-columns))))


(defn start [args]
  (create-tables)

  ::started)

(defn stop []
  ::stopped)
