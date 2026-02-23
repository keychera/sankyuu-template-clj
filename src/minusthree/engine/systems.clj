(ns minusthree.engine.systems
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.gl.texture :as texture]
   [minusthree.model.model3d :as model3d]
   [minusthree.stage.sankyuu :as sankyuu]
   [minusthree.engine.camera :as camera]))

(def all
  [time/system
   loading/system
   texture/system
   t3d/system
   camera/system
   
   model3d/all-system
   
   sankyuu/system])
