(ns minusthree.engine.systems
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.gl.texture :as texture]))

(def all
  [time/system
   loading/system
   texture/system])
