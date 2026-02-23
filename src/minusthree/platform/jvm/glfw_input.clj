(ns minusthree.platform.jvm.glfw-input
  (:require
   [minusthree.platform.jvm.input.scroll :as scroll]
   [clojure.spec.alpha :as s]
   [minusthree.engine.input :as input])
  (:import
   [org.lwjgl.glfw GLFW GLFWScrollCallbackI]))

(s/def ::glfw-inputs* #(instance? clojure.lang.Atom %))

(defn on-scroll [glfw-inputs* _window xoffset yoffset]
  (swap! glfw-inputs* assoc
         ::scroll/xoffset xoffset
         ::scroll/yoffset yoffset))

(defn set-callbacks
  [{:keys [glfw-window] :as game}]
  (println "set glfw input callbacks")
  (let [glfw-inputs* (atom nil)]

    (doto glfw-window
      (GLFW/glfwSetScrollCallback
       (reify GLFWScrollCallbackI
         (invoke [_this window xoffset yoffset]
           (on-scroll glfw-inputs* window xoffset yoffset)))))

    (assoc game ::glfw-inputs* glfw-inputs*)))

(defn supply [{::keys [glfw-inputs*] :as game}]
  (if-let [input-events @glfw-inputs*]
    (do (reset! glfw-inputs* nil)
        (input/queue game input-events))
    game))
