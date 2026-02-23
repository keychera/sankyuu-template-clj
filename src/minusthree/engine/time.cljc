(ns minusthree.engine.time
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.macros :refer [s->]]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::total number?)
(s/def ::delta number?)

(def fps 1)
(def timestep-ms (/ 1000 fps))
(s/def ::step int)
(s/def ::step-delay number?)

(defn update-time [game total delta]
  (-> (assoc game ::total total ::delta delta)
      (update ::world/this o/insert ::now {::total total ::delta delta})))

(defn init-fn [world _game]
  (-> world
      (o/insert ::now {::step 1 ::step-delay 0})))

(def rules
  (o/ruleset
   {::timestep
    [:what
     [::now ::total tt]
     [::now ::delta dt]
     [::now ::step timestep {:then false}]
     [::now ::step-delay delay-ms {:then false}]
     :then 
     (if (> delay-ms timestep-ms)
       (s-> session
            (o/insert ::now ::step (inc timestep))
            (o/insert ::now ::step-delay 0))
       (s-> session
            (o/insert ::now ::step-delay (+ delay-ms dt))))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})
