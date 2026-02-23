(ns minusthree.engine.world
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(s/def ::this ::o/session)

(s/def ::init-fn fn? #_(fn [world game] world))
(s/def ::post-fn fn? #_(fn [world game] world)) ;; post-[refresh]fn
(s/def ::rules   ::o/rules)

;; dev-only
(defn resolve-var [v]
  (if (instance? clojure.lang.Var v) (deref v) v))

(defn prepare-world [world game all-rules init-fns]
  (let [world  (reduce o/add-rule (or world (o/->session)) all-rules)
        world' (reduce (fn [w' {::keys [init-fn post-fn]}]
                         (cond-> w'
                           init-fn (init-fn game)
                           post-fn (post-fn game)))
                       world init-fns)]
    world'))

(defn init-world [game system-coll]
  (let [systems   (into [] (map resolve-var) (flatten (deref system-coll)))
        all-rules (into [] (mapcat (comp resolve-var ::rules)) systems)
        init-fns  (into [] (map #(select-keys % [::init-fn ::post-fn])) systems)]
    (assoc (update game ::this prepare-world game all-rules init-fns)
           ::init-fns init-fns)))

(defn post-world
  "post-[refresh] world. this only makes sense in REPL dev.
   in normal runtime, this is just another init-fn"
  [{::keys [init-fns] :as game}]
  (reduce (fn [game' post-fn] (update game' ::this post-fn game'))
          game (into [] (comp (map ::post-fn) (filter some?)) init-fns)))

;; esse, short for 'essence', has similar connotation to entity in an entity-component-system
;; however, this game is built on top of a rules engine, it doesn't actually mean anything inherently
;; here, an esse is often referring to something that has the same id in the rules engine
;; (also, I've never used an ecs before so I am not sure if this is actually similar)

(s/def ::esse-id qualified-keyword?)

(defn esse
  "insert an esse given the facts in the shape of maps of attr->value.
   this fn is merely sugar, spice, and everything nice"
  [world esse-id & facts]
  (o/insert world esse-id (apply merge facts)))
