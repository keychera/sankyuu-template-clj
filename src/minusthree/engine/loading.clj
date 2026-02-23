(ns minusthree.engine.loading
  (:require
   [clojure.core.async :refer [>!! thread chan poll!]]
   [clojure.spec.alpha :as s]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::channel some? #_ManyToManyChannel)
(s/def ::load-fn fn? #_(fn [] (s/coll-of facts)))
(s/def ::state #{:pending :loading :success :error})
(def load-buffer 8)

(defn push [load-fn]
  {::load-fn load-fn ::state :pending})

(defn init-channel [game]
  (assoc game ::channel (chan 2)))

(def rules
  (o/ruleset
   {::to-load
    [:what
     [esse-id ::load-fn load-fn]
     [esse-id ::state :pending]]}))

(def system
  {::world/rules #'rules})

(defn loading-zone [game]
  (let [loading-ch (::channel game)
        new-load   (poll! loading-ch)]
    (if new-load
      (let [{:keys [esse-id new-facts]} new-load]
        (update game ::world/this
                (fn [world]
                  (-> (reduce o/insert world new-facts)
                      (o/insert esse-id ::state :success)))))
      (let [world    (::world/this game)
            to-loads (into [] (take load-buffer) (o/query-all world ::to-load))]
        (when (seq to-loads)
          (thread
            (doseq [{:keys [esse-id load-fn]} to-loads]
              (try
                (let [loaded-facts (load-fn)]
                  (>!! loading-ch {:esse-id esse-id :new-facts loaded-facts}))
                (catch Throwable err
                  (println esse-id "load error! cause:" (:cause (Throwable->map err)))
                  (>!! loading-ch {:esse-id esse-id :new-facts [[esse-id ::state :error]]}))))))
        (update game ::world/this
                (fn [world]
                  (reduce (fn [w' {:keys [esse-id]}] (o/insert w' esse-id ::state :loading)) world to-loads)))))))
