(ns minusthree.platform.jvm.glfw
  (:require
   [minusthree.engine.engine :as engine]
   [minusthree.engine.time :as time]
   [minusthree.platform.jvm.glfw-input :as glfw-input])
  (:import
   [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback]
   [org.lwjgl.opengl GL GL42]))

(defn ms-windows? []
  (.startsWith (System/getProperty "os.name") "Windows"))

(defn create-window
  "return handle of the glfw window"
  ([] (create-window {}))
  ([{:keys [w h x y floating? title]
     :or   {w 1280 h 720 x 100 y 100 title "Hello!"}}]
   (.. (GLFWErrorCallback/createPrint System/err) set)
   (when-not (GLFW/glfwInit)
     (throw (ex-info "Unable to initialize GLFW" {})))
   (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
   (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
   (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 4)
   (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 2)
   (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL42/GL_TRUE)
   (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
   (GLFW/glfwWindowHint GLFW/GLFW_SAMPLES 4)
   (let [window (GLFW/glfwCreateWindow w h title 0 0)]
     (when (or (nil? window) (zero? window))
       (throw (ex-info "Failed to create GLFW window" {})))
     (when (ms-windows?) (GLFW/glfwSetWindowPos window x y))
     (GLFW/glfwMakeContextCurrent window)
     (GLFW/glfwSwapInterval 1) ;; vsync
     (GL/createCapabilities)
     (when floating? (GLFW/glfwSetWindowAttrib window GLFW/GLFW_FLOATING GLFW/GLFW_TRUE))
     window)))

(defn start-glfw-loop
  ([glfw-window {:keys [stop-flag* :refresh-flag*] :as config}]
   (println "hello -3 + glfw")
   (letfn [(we-begin-the-game []
             (GLFW/glfwShowWindow glfw-window)
             (-> {::time/total    0.0
                  :config        (dissoc config :stop-flag*)
                  :glfw-window   glfw-window
                  :refresh-flag* refresh-flag*}
                 (glfw-input/set-callbacks)))

           (do-we-stop? [_game]
             (or (GLFW/glfwWindowShouldClose glfw-window)
                 (and (some? stop-flag*) @stop-flag*)))

           (do-we-refresh? [_game]
             (let [refresh? (some-> refresh-flag* deref)]
               (when refresh? (reset! refresh-flag* false))
               refresh?))

           (things-from-out-there [game]
             (let [total (* (GLFW/glfwGetTime) 1000)
                   delta (- total (::time/total game))
                   game  (time/update-time game total delta)]
               (GLFW/glfwSwapBuffers glfw-window)
               (GLFW/glfwPollEvents)
               (GLFW/glfwSetWindowTitle glfw-window (str "frametime(ms): " delta))
               (-> game
                   (glfw-input/supply))))

           (the-game-ends []
             (Callbacks/glfwFreeCallbacks glfw-window)
             (GLFW/glfwDestroyWindow glfw-window)
             (GLFW/glfwTerminate))]

     (engine/game-loop
      #::engine{:we-begin-the-game we-begin-the-game
                :do-we-stop? do-we-stop?
                :do-we-refresh? do-we-refresh?
                :things-from-out-there things-from-out-there
                :the-game-ends the-game-ends}))))
