(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.rendering :as rendering]
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]
   [minusthree.engine.input :as input]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(s/def
  ::we-begin-the-game ;; by providing an
  fn?
  ;; of zero arity that return the initial game state
  ;; this may have side effect of anything you wish
  )

(s/def ;; we ask ourself
  ::do-we-stop? ;; by calling this
  fn?
  ;; of one arity, accepting the game state, returning bool
  ;; return true to stop the game
  )

(s/def ;; we ask ourself
  ::do-we-refresh? ;; by calling this
  fn?
  ;; of one arity, accepting the game state, returning bool
  ;; refresh is dev time niceties to recall fn post-refresh without destroy, and rebuild arena
  ;; return true to trigger post-refresh
  )

(s/def ;; the game will loop and
  ;; the engine will gather every
  ::things-from-out-there ;; by calling an
  fn?
  ;; of one arity, accepting the game state, returning the updated game
  ;; this might include timing, input, platform changes, dev time flags, etc.
  )

(s/def ;; lastly
  ::the-game-ends ;; the engine will call an
  fn? ;; of zero arity to do cleanup 
  )

(s/def ::game-loop-config
  (s/keys :req [::we-begin-the-game
                ::do-we-stop?
                ::do-we-refresh?
                ::things-from-out-there
                ::the-game-ends]))

;; TODO design, currently we don't allow anything to leak 
;; outside of the game loop except via exception
;; in devtime, exception will be catch by minusthree.-dev.start
;; and it will be presented by viscous/inspect

(declare init post-refresh tick pre-refresh destroy)

(defn game-loop
  [{::keys [we-begin-the-game
            do-we-stop? do-we-refresh?
            things-from-out-there
            the-game-ends]
    :as game-loop-config}]
  (s/assert ::game-loop-config game-loop-config)
  (try
    (loop [game (we-begin-the-game) first-init? true]
      (let [[loop-state game']
            (loop [game' (-> game
                             (cond-> first-init? (init))
                             (post-refresh))]
              (cond
                (do-we-stop? game')    [::stopping   (-> game' pre-refresh destroy)]
                (do-we-refresh? game') [::refreshing (-> game' pre-refresh)]
                :else (let [updated-game (things-from-out-there game')]
                        (recur (tick updated-game)))))]
        (condp = loop-state
          ::refreshing (recur game' false)
          ::stopping   ::ending)))
    (finally
      (the-game-ends))))

(defn init [{:keys [::world/this] :as game}]
  (->> (or this (world/init-world game #'systems/all))
       (loading/init-channel)
       (s/assert ::init-game)))

(defn post-refresh [new-game]
  (->> new-game
       (rendering/init)
       (input/init)
       (world/post-world)))

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (loading/loading-zone)
      (input/input-zone)
      (rendering/rendering-zone)))

(defn pre-refresh [old-game]
  (rendering/destroy old-game)
  old-game)

(defn destroy [game]
  (-> game))
