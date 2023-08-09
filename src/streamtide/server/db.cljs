(ns streamtide.server.db
  "Module for defining database structure and managing and abstracting queries to the database"
  (:require [district.server.config :refer [config]]
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
(def big-numbers-fields [:round/matching-pool
                         :round/distributed
                         :matching/amount
                         :leader/donation-amount
                         :leader/matching-amount
                         :leader/total-amount
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
   [:user/perks :varchar default-nil]
   [:user/min-donation :unsigned :integer default-nil]
   [:user/blacklisted :tinyint default-false]])

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
   [:round/id :unsigned :integer]
   [(sql/call :foreign-key :donation/sender) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :donation/receiver) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :round/id) (sql/call :references :round :round/id)]])

(def matching-columns
  [[:matching/id :integer primary-key :autoincrement]
   [:matching/receiver address not-nil]
   [:matching/date :timestamp not-nil]
   [:matching/amount :unsigned :integer not-nil]  ;; TODO use string to avoid precision errors? order-by is important
   [:matching/coin address not-nil]
   [:round/id :unsigned :integer]
   [(sql/call :foreign-key :matching/receiver) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :foreign-key :round/id) (sql/call :references :round :round/id)]])

(def user-roles-columns
  [[:role/id :integer primary-key :autoincrement]
   [:user/address address not-nil]
   [:role/role :string not-nil]
   [(sql/call :foreign-key :user/address) (sql/call :references :user :user/address) (sql/raw "ON DELETE CASCADE")]
   [(sql/call :unique :user/address :role/role)]])

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
   [:round/duration :unsigned :integer not-nil]
   [:round/matching-pool :unsigned :integer not-nil]   ;; TODO use string to avoid precision errors? order-by is important
   [:round/distributed :unsigned :integer]])

(def events-columns
  [[:event/contract-key :varchar not-nil]
   [:event/event-name :varchar not-nil]
   [:event/last-log-index :integer not-nil]
   [:event/last-block-number :integer not-nil]
   [:event/count :integer not-nil]
   [(sql/call :primary-key :event/contract-key :event/event-name)]])


(def user-column-names (filter keyword? (map first user-columns)))
(def social-link-column-names (filter keyword? (map first social-link-columns)))
(def grant-column-names (filter keyword? (map first grant-columns)))
(def content-column-names (filter keyword? (map first content-columns)))
(def donation-column-names (filter keyword? (map first donation-columns)))
(def matching-column-names (filter keyword? (map first matching-columns)))
(def user-roles-column-names (filter keyword? (map first user-roles-columns)))
(def user-content-permission-column-names (filter keyword? (map first user-content-permission-columns)))
(def announcement-column-names (filter keyword? (map first announcement-columns)))
(def round-column-names (filter keyword? (map first round-columns)))
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

(defn get-users [{:keys [:user/name :user/address :user/blacklisted :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:*]
                 :from [:user]}
                (some? blacklisted) (sqlh/merge-where [:= :user/blacklisted blacklisted])
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

(defn get-leaders [{:keys [:round :search-term :order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        sub-query-donations (cond-> {:select [:donation/receiver [(sql/call :sum :donation/amount) :donations]]
                                     :from [:donation] :group-by [:donation/receiver]}
                                    round (sqlh/merge-where [:= :donation.round/id round]))
        sub-query-matchings (cond-> {:select [:matching/receiver [(sql/call :sum :matching/amount) :matchings]]
                                     :from [:matching] :group-by [:matching/receiver]}
                                    round (sqlh/merge-where [:= :matching.round/id round]))
        query (cond->
                {:select [:u.*
                          [(sql/call :coalesce :donations 0) :leader/donation-amount]
                          [(sql/call :coalesce :matchings 0) :leader/matching-amount]
                          [(sql/call :+ (sql/call :coalesce :donations 0) (sql/call :coalesce :matchings 0)) :leader/total-amount]]
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
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-round [round-id]
  (db-get {:select [:*]
           :from [:round]
           :where [:= round-id :round.round/id]}))

(defn get-rounds [{:keys [:order-by :order-dir :first :after] :as args}]
  (let [page-start-idx (when after (js/parseInt after))
        page-size first
        query (cond->
                {:select [:r.*]
                 :from [[:round :r]]}
                order-by (sqlh/merge-order-by [[(get {:rounds.order-by/date :r.round/start
                                                      :rounds.order-by/matching-pool :r.round/matching-pool
                                                      :rounds.order-by/id :r.round/id}
                                                     order-by)
                                                (or (keyword order-dir) :asc)]]))]
    (paged-query query page-size page-start-idx)))

(defn get-user-content-permissions [{:keys [:user/source-user :user/target-user]}]
  (db-all (cond->
            {:select [:*]
             :from [[:user-content-permissions :ucp]]}
            source-user (sqlh/merge-where [:= :ucp.user/source-user source-user])
            target-user (sqlh/merge-where [:= :ucp.user/target-user target-user]))))

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
              :values [user-info]
              :upsert {:on-conflict [:user/address]
                       :do-update-set (keys user-info)}})))

(defn upsert-users-info! [args]
  (log/debug "user-upsert-users-info!" args)
  (let [user-infos (map #(select-keys % user-column-names) args)]
    (db-run! {:insert-into :user
              :values user-infos
              :upsert {:on-conflict [:user/address]
                       :do-update-set user-column-names}})))

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


(defn add-user-content-permission! [{:keys [:user/source-user :user/target-user] :as args}]
  (db-run! {:insert-into :user-content-permissions
            :values [(select-keys args user-content-permission-column-names)]
            :upsert {:on-conflict [:user/source-user :user/target-user]
                     :do-nothing []}}))

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

  (db-run! (-> (psqlh/create-table :user-content-permissions :if-not-exists)
               (psqlh/with-columns user-content-permission-columns)))

  (db-run! (-> (psqlh/create-table :announcement :if-not-exists)
               (psqlh/with-columns announcement-columns)))

  (db-run! (-> (psqlh/create-table :round :if-not-exists)
               (psqlh/with-columns round-columns)))

  (db-run! (-> (psqlh/create-table :events :if-not-exists)
               (psqlh/with-columns events-columns))))


(defn start [args]
  (create-tables)

  ::started)

(defn stop []
  ::stopped)
