(ns minusthree.gl.gl-magic
  (:require
   [clojure.core.match :as match]
   [clojure.spec.alpha :as s]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture])
  (:import
   [org.lwjgl.opengl GL45]))

(s/def ::casted? boolean?)
(s/def ::data some?)
(s/def ::facts some?)
(s/def ::vao some?)

(defn cast-spell [spell-chants]
  (-> (reduce
       (fn [{:keys [state] :as magician} chant]
         (match/match [chant]
           [{:bind-vao _}] ;; entry: vao binding
           (let [vao (GL45/glGenVertexArrays)]
             (GL45/glBindVertexArray vao)
             (assoc-in magician [::data ::vao (:bind-vao chant)] vao))

           [{:buffer-data _ :buffer-type _}] ;; entry: buffer binding
           (let [buffer (GL45/glGenBuffers)
                 buffer-type (:buffer-type chant)
                 buffer-data (:buffer-data chant)]
             (GL45/glBindBuffer buffer-type buffer)
             (GL45/glBufferData buffer-type buffer-data (or (:usage chant) GL45/GL_STATIC_DRAW))
             (cond-> magician
               (:buffer-name chant) (assoc-in [::data ::shader/buffer (:buffer-name chant)] buffer)
               true (update :state assoc :current-buffer buffer :buffer-type buffer-type)))

           [{:bind-current-buffer _}]
           (let [current-buffer (:current-buffer state)
                 buffer-type    (:buffer-type state)]
             (GL45/glBindBuffer buffer-type current-buffer)
             magician)

           ;; entry: attrib pointing, some keywords follows gltf accessor keys 
           [{:point-attr _ :count _ :component-type _ :use-shader _}]
           (try
             (s/assert ::shader/program-info (:use-shader chant))
             (if-let [attr-loc (get-in (:use-shader chant) [:attr-locs (:point-attr chant) :attr-loc])]
               (let [{:keys [count component-type stride offset] :or {stride 0 offset 0}} chant]
                 (condp = component-type
                   GL45/GL_UNSIGNED_SHORT (GL45/glVertexAttribIPointer attr-loc count component-type stride offset)
                   GL45/GL_UNSIGNED_INT   (GL45/glVertexAttribIPointer attr-loc count component-type stride offset)
                   (GL45/glVertexAttribPointer attr-loc count component-type false stride offset))
                 (GL45/glEnableVertexAttribArray attr-loc)
                 magician)
               (update-in magician [::data ::err] conj (str "[error] attr-loc not found for chant:" chant)))
             (catch Throwable err
               (println "[error] in point-attr" (:point-attr chant) ", cause:" (:cause (Throwable->map err)))
               (update-in magician [::data ::err] conj err)))

           [{:bind-texture _ :image _ :for-esse _}] ;; entry: texture binding
           (let [uri      (-> chant :image :uri)
                 tex-name (:bind-texture chant)]
             (update magician ::facts conj
                     [tex-name ::texture/uri-to-load uri]
                     [tex-name ::texture/for (:for-esse chant)]))

           [{:unbind-vao _}]
           (do (GL45/glBindVertexArray 0)
               magician)

           [{:insert-facts _}]
           (let [facts (:insert-facts chant)]
             (println "gl-magic will insert" (count facts) "fact(s)")
             (update magician ::facts into facts))

           #_"no else handling. m/match will throw on faulty spell."))
       {::data {} ::facts [] :state {}}
       spell-chants)
      (dissoc :state)))
