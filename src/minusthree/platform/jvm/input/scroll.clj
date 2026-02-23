(ns minusthree.platform.jvm.input.scroll 
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::xoffset  number?)
(s/def ::yoffset number?)
