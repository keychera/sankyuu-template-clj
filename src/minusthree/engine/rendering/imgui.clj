(ns minusthree.engine.rendering.imgui
  (:require
   [minusthree.engine.camera :as camera]
   [minusthree.engine.rendering.fps-panel :as fps-panel])
  (:import
   [imgui ImGui]
   [imgui.extension.imguizmo ImGuizmo]
   [imgui.extension.imguizmo ImGuizmo]
   [imgui.flag ImGuiConfigFlags ImGuiWindowFlags]
   [imgui.flag ImGuiConfigFlags ImGuiWindowFlags]
   [imgui.gl3 ImGuiImplGl3]
   [imgui.glfw ImGuiImplGlfw]))

(def identity-mat (float-array
                   [1.0 0.0 0.0 0.0
                    0.0 1.0 0.0 0.0
                    0.0 0.0 1.0 0.0
                    0.0 0.0 0.0 1.0]))

(defn init [{:keys [glfw-window] :as game}]
  (let [imGuiGlfw (ImGuiImplGlfw.)
        imGuiGl3  (ImGuiImplGl3.)]
    (ImGui/createContext)
    (doto (ImGui/getIO)
      (.addConfigFlags ImGuiConfigFlags/DockingEnable)
      (.setConfigWindowsMoveFromTitleBarOnly  true)
      (.setFontGlobalScale 1.0))
    (doto imGuiGlfw
      (.init glfw-window true)
      (.setCallbacksChainForAllWindows true))
    (.init imGuiGl3 "#version 300 es")
    (assoc game
           ::imGuiglfw imGuiGlfw
           ::imGuiGl3 imGuiGl3)))

(defn imGuizmoPanel [view* project* cam-distance w h]
  (ImGuizmo/beginFrame)

  (ImGui/setNextWindowPos 0.0 0.0)
  (ImGui/setNextWindowSize (float w) (float h))
  (when (ImGui/begin "imguizmo"
                     (bit-or ImGuiWindowFlags/NoTitleBar
                             ImGuiWindowFlags/NoMove
                             ImGuiWindowFlags/NoBringToFrontOnFocus))
    (let [manip-x (+ w -150.0)
          manip-y 16.0]
      (ImGuizmo/setOrthographic false)
      (ImGuizmo/enable true)
      (ImGuizmo/setDrawList)
      (ImGuizmo/setRect 0.0 0.0 w h)
      (ImGuizmo/drawGrid view* project* identity-mat 100)
      (ImGuizmo/setID 0)
      (ImGuizmo/viewManipulate view* cam-distance manip-x manip-y 128.0 128.0 0x70707070))

    :-)

  (ImGui/end))

(defn frame [{::keys [imGuiGl3 imGuiglfw]
              :keys  [config]
              :as game}]
  (.newFrame imGuiglfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (let [{:keys [view* project* distance]} (camera/get-active-cam game)
        {:keys [w h]} (:window-conf config)]
    (when (and view* project* distance)
      (imGuizmoPanel view* project* distance w h)))
  (let [{:keys [title text]} (:imgui config)]
    (fps-panel/render! title text))
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData)))

(defn destroy [{::keys [imGuiGl3 imGuiglfw]}]
  (println "destroy imgui context")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiglfw (.shutdown imGuiglfw))
  (ImGui/destroyContext))
