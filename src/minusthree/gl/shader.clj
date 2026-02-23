(ns minusthree.gl.shader
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [instaparse.core :as insta])
  (:import
   [java.lang Integer]))

(s/def ::glsl-type #{:float :int :uint :bool
                     :vec2 :vec3 :vec4
                     :ivec2 :ivec3 :ivec4
                     :uvec2 :uvec3 :uvec4
                     :bvec2 :bvec3 :bvec4
                     :mat2 :mat3 :mat4
                     :sampler2D})
(s/def ::type (s/or :raw-type ::glsl-type
                    :arr-type (s/cat :type ::glsl-type :dimension int?)))

(s/def ::program int?)

(s/def ::attr-loc int?)
(s/def ::attr (s/keys :req-un [::type ::attr-loc]))
(s/def ::attr-locs (s/map-of keyword? ::attr))

(s/def ::uni-loc int?)
(s/def ::uni (s/keys :req-un [::type ::uni-loc]))
(s/def ::uni-locs (s/map-of keyword? ::uni))

(s/def ::program-info (s/keys :req-un [::program ::attr-locs ::uni-locs]))

(s/def ::buffer some?)

(def shader-header-grammar
  "
Header = Block+

Block = InDecl
      | OutDecl
      | UniformDecl
      | InterfaceBlockDecl
      | ConstDecl

MemberDecl = TypeDecl MemberName <';'>
MemberName = Ident
InDecl = Layout? 'in' MemberDecl
OutDecl = Layout? 'out' MemberDecl
UniformDecl = Layout? 'uniform' MemberDecl
ConstDecl = 'const' TypeDecl MemberName <'='> Anything+ <';'>

InterfaceBlockDecl = Layout? StorageQualifier BlockName
  <'{'> InterfaceMembers <'}'> InstanceName? <';'>
InterfaceMembers = MemberDecl+
StorageQualifier = 'uniform'
BlockName = Ident
InstanceName = Ident

Layout = <'layout'> <'('> LayoutQualifier+ <')'>
LayoutQualifier = Ident | LocationDecl
LocationDecl = #'location=[0-9]+'

TypeDecl = TypeSpec ArraySpec?

TypeSpec = #'(void|int|float|(u*(vec|mat)[234])|sampler2D)'
ArraySpec = <'['> Number <']'>
Ident = #'[a-zA-Z_][a-zA-Z0-9_]*'
Number = #'[0-9.]+'
Operator = #'[+*-/]'
Anything = Ident | Number | Operator
")

(def ^:private whitespace (insta/parser "whitespace = #'\\s+'"))
(def ^:private parser (insta/parser shader-header-grammar :auto-whitespace whitespace))

(defn parse-header [source]
  (let [tree (parser source)
        tree (insta/transform
              (letfn [(qualify
                        ([qualifier member]
                         (assoc member (keyword qualifier) true))
                        ([_layout qualifier member]
                         (assoc member (keyword qualifier) true)))
                      (mapify [& nodes] (into {} nodes))]
                {:TypeSpec           keyword
                 :MemberName         (fn [[_ member-name]] [:member-name (keyword member-name)])
                 :BlockName          (fn [[_ member-name]] [:member-name (keyword member-name)])
                 :ArraySpec          (comp Integer/parseInt second)
                 :TypeDecl           (fn [& nodes]
                                       (let [member-type (into [] nodes)
                                             member-type (if (= (count member-type) 1) (first member-type) member-type)]
                                         (vector :member-type member-type)))
                 :StorageQualifier   (fn [member-type] [(keyword (str member-type "-block")) true])
                 :MemberDecl         mapify
                 :InterfaceBlockDecl mapify
                 :UniformDecl        qualify
                 :InDecl             qualify
                 :OutDecl            qualify
                 :Header             (fn [& blocks] (into [] (map second) blocks))})
              tree)]
    (when (insta/failure? tree)
      (throw (ex-info (pr-str tree) {})))
    tree))

(defn get-header-source [source]
  ;; just a heuristic split that works for now
  (let [[_ body] (str/split source #"precision \w* float;\r?\n")
        [header _] (str/split body #"(void|int|float|(u*(vec|mat)[234])|sampler[23]D)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(")]
    header))

(comment
  (let [shader
        "#version 300 es
precision mediump float;
uniform sampler2D u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
uniform mat4[500] u_joint_mats;
layout(std140) uniform matrix {
    mat4 mvp;
} mat;
layout(std140) uniform Skinning {
    mat4[500] u_joint_mats;
};
const float radius = 15.0;
const float radius2 = radius * radius;
layout(location=0) in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;
in vec4 WEIGHTS_0;
in uvec4 JOINTS_0;
out vec3 Normal;
out vec2 TexCoords;
float main()
{
  vec4 pos = (vec4(POSITION, 1.0));
  mat4 skin_mat = ((WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z]) + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]));
  vec4 world_pos = (u_model * skin_mat * pos);
  vec4 cam_pos = (u_view * world_pos);
  gl_Position = (u_projection * cam_pos);
  Normal = NORMAL;
  TexCoords = TEXCOORD_0;
}"]
    (-> shader get-header-source parse-header))

  :-)