(ns xander.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.data.xml :as cdxml])
  (:import (java.text SimpleDateFormat)))

(def tag-value-key :tag/value)
(def attrs-key :_attrs)
(def all-func-key "*")

(defn lvs?
  "Determines if x is a vector, list, set, or seq."
  [x]
  (or (vector? x) (list? x) (set? x) (seq? x)))

(defn ->str
  "Converts x to string."
  [x]
  (if (keyword? x)
    (-> x str (subs 1))
    (str x)))

(defn ->kw
  "Convert x to keyword."
  [x]
  (if (keyword? x)
    x
    (keyword (-> x
                 str
                 str/lower-case
                 (str/replace #" " "-")))))

(defn conjv
  "Conjoins x to c, defaults conjoing on an empty vector if c is nil."
  [c x]
  (conj (or c []) x))

(defn smart-conj
  "Conjoins x to c, if c is a map, then return [c x]."
  [c x]
  (if c
    (if (map? c)
      [c x]
      (conjv c x))
    x))

(defn ->input-stream
  "Coverts x to a string and then into an input stream."
  [x]
  (io/input-stream (.getBytes (str x))))

(defn tac->element
  "Converts a simple Clojure XML map with :tag, :attrs, and :content
  to Clojure Data XML element."
  [m]
  (cdxml/element (get m :tag) (get m :attrs) (get m :content)))

(defn <-attrs
  "Extracts attribute keys of a tag location ts from attributes map am."
  [am ts & [default]]
  (get-in am (conj ts attrs-key) default))

; ---------------------------------------

(declare map->cxml) ; forward declaration of map->cxml to handle bi-directional references

(defn ->tag-content
  "Converts tag t to content c given optional attribute map am."
  [t c & [am ts]]
  (let [nts (conj (or ts []) t)]
    (if-not (lvs? c)
      (if-not (map? c)
        [{:tag t
          :attrs nil
          :content [(->str c)]}]
        [(map->cxml c am t ts)])
      (reduce (fn [a x] (conj (or a []) (->tag-content t x am ts)))
              nil
              c))))

(defn map->cxml2
  "Converts a standard Clojure map cm to an XML string representation given the top-level tag and attribute map am."
  [tag cm & [am ts]]
  (let [nts (conj (or ts []) tag)]
    {:tag tag
     :attrs nil
     :content (vec (flatten (reduce-kv (fn [a k v] (concat (or a []) (->tag-content k v am nts)))
                                       nil
                                       cm)))}))

(def default-outer-tag :document)
(def outer-tag (atom default-outer-tag))

(defn map->cxml
  "Converts a standard Clojure map cm to an XML string representation given the top-level tag and attribute map am."
  [cm & [am tag ts]]
  (let [outer-cm (when (= (count cm) 1)
                   (-> cm first first))
        ncm (if (and outer-cm (not tag)) (get cm outer-cm) cm)
        cmt (or tag outer-cm @outer-tag)
        nts (conj (or ts []) cmt)
        cmx (vec (flatten (reduce-kv (fn [a k v] (concat (or a []) (->tag-content k v am nts)))
                                     nil
                                     ncm)))]
    {:tag cmt :attrs nil :content cmx}))

(defn cxml->attrs-cxml
  "Converts Clojure XML-compliant map to XML-compliant map with attributes pulled
  into attributes map of tag using attribute mapping am and tag location ts."
  [cxml & [am ts]]
  (let [cts (conj (or ts []) (get cxml :tag))
        as (set (<-attrs am cts))
        acs (reduce (fn [a x]
                      (let [ctag (get x :tag)]
                        (if (contains? as ctag)
                          (assoc a ctag (first (get x :content)))
                          a)))
                    nil
                    (get cxml :content))]
    (-> cxml
        (assoc :attrs acs) ;; adds attributes to tag
        (update :content (fn [tm] ;; removes attributes from tag contents
                           (vec
                             (filter (fn [xx]
                                       (if (map? xx)
                                         (not (contains? as (get xx :tag)))
                                         true))
                                     tm))))
        (update :content (fn [x] ;; recursively generates children tags
                           (vec
                             (map (fn [cx]
                                    (if (map? cx)
                                      (cxml->attrs-cxml cx am cts)
                                      cx))
                                  x)))))))

(defn cxml->elements
  "Converts a Clojure XML-compliant map cxmap to a Clojure Data XML element map."
  [cxmap]
  (tac->element (update cxmap :content #(vec (reduce (fn [a x]
                                                       (conj (or a [])
                                                             (if (map? x)
                                                               (cxml->elements x)
                                                               x)))
                                                     nil
                                                     %)))))

(defn ->xml-element-str
  "Convert Clojure XML-compliant map cxml to elements and then returns an indented
  formatted XML string."
  [cxml]
  (cdxml/indent-str (cxml->elements cxml)))

(defn ->xml-str
  "Converts an XML-compliant map to an XML string representation."
  [cxml]
  (->xml-element-str cxml))

(defn ->cxml
  "Converts source XML file x or raw string XML x to interim Clojure XML representation."
  [x]
  (xml/parse (if (str/starts-with? x "<?")
               (->input-stream x)
               x)))

(defn parse-elements
  [x & [delim]]
  (vec (->> (str/split x (or delim #",")) (map str/trim))))

(defn parse-and-transform-elements
  [tf x & [delim]]
  (vec (->> (str/split x (or delim #",")) (map str/trim) (map tf))))

(defn ->smart-external-value
  [v]
  (if (or (map? v) (lvs? v))
    v
    (->str v)))

;; date patterns ================================
(def unix-date-pattern "yyyy-MM-dd'T'HHmmssZ")
(def unix-date-millis-pattern "yyyy-MM-dd'T'HHmmss.SSSZ")
(def unix-date-stop-pattern "yyyy-MM-dd'T'HHmmss.SSSZ")
(def us-slash-date-only-pattern "MM/dd/yyyy")
(def unix-date-only-start-suffix-pattern "T00:00:00+0000")
(def unix-date-only-stop-suffix-pattern "T23:59:59.999+0000")
(def sdf-unix (SimpleDateFormat. unix-date-pattern))
(def sdf-unix-millis (SimpleDateFormat. unix-date-millis-pattern))
(def sdf-stop-unix (SimpleDateFormat. unix-date-stop-pattern))
(def sdf-us-slash-date (SimpleDateFormat. us-slash-date-only-pattern))

;; safe conversions / normalizations
(defn safe-passthru
  "Applies function f to x.  If an exception occurs during f application, returns x."
  [f x]
  (try
    (f x)
    (catch Exception e
      x)))

(def safe->kw (partial safe-passthru ->kw))
(def safe-code->kw (partial safe-passthru #(-> % str str/lower-case keyword)))
(def safe-long (partial safe-passthru #(Long/parseLong %)))
(def safe-double (partial safe-passthru #(Double/parseDouble %)))
(def safe-flag (partial safe-passthru #(let [ls (str/lower-case %)]
                                         (or (= "true" ls)
                                             (= "yes" ls)
                                             (= "y" ls)
                                             (= "t" ls)
                                             (= "1" ls)))))
(def safe-slash-date (partial safe-passthru #(.parse sdf-us-slash-date %)))
(def safe-date (partial safe-passthru #(.parse sdf-unix (str/replace % #":" ""))))
(def safe-millis-date (partial safe-passthru #(.parse sdf-unix-millis (str/replace % #":" ""))))
(def safe-start-date (partial safe-passthru #(.parse sdf-unix (str/replace (str %
                                                                                unix-date-only-start-suffix-pattern)
                                                                           #":" ""))))
(def safe-stop-date (partial safe-passthru #(.parse sdf-stop-unix (str/replace (str %
                                                                                    unix-date-only-stop-suffix-pattern)
                                                                               #":" ""))))
(def safe-parse-elements (partial safe-passthru parse-elements))
(def safe-parse-long-elements (partial safe-passthru (partial parse-and-transform-elements safe-long)))
(def safe-parse-double-elements (partial safe-passthru (partial parse-and-transform-elements safe-double)))
(def safe-parse-kw-elements (partial safe-passthru (partial parse-and-transform-elements safe->kw)))
(def safe-parse-flag-elements (partial safe-passthru (partial parse-and-transform-elements safe-flag)))

;; transform field functions -----------------------------------------------------------------------

(defn generate-transform-field-funcs
  "Generates transform fields funcs by inversing the value list as keys."
  [ffm]
  (reduce-kv (fn [a k fs]
               (reduce (fn [aa ff]
                         (assoc aa ff k))
                       a
                       fs))
             nil
             ffm))

(defn transform-field
  "Transforms value given a key k to lookup up transform function in map tff."
  [k v tfm]
  (let [all-f (get tfm all-func-key)]
    (if-let [f (get tfm k)]
      (let [fv (f v)]
        (if all-f
          (all-f fv)
          fv))
      (if all-f
        (all-f v)
        v))))

(defn transform-map-fields
  "Transforms map fields given a map with field as keys and function as value."
  [m tfm]
  (reduce-kv (fn [a k v]
               (assoc a k (transform-field k v tfm)))
             nil
             m))

(defn thread-fs
  "Composes and applies functions fs to seed argument x in order of fs left-to-right."
  [fs x]
  ((apply comp (reverse fs)) x))

(defn transform-keys
  "Transforms key of map (recursively) given a list of functions fs."
  [fs m]
  (reduce-kv (fn [a k v]
               (let [tk (thread-fs fs k)]
                 (assoc a tk (if-not (map? v)
                               v
                               (transform-keys fs m)))))
             nil
             m))

(def kebab-keys "Transforms map keys into Clojure kebab-keys." (partial transform-keys [->kw]))

(defn trim-content
  "Trims the text content of an Clojure XML map m for extraneous white space."
  [m]
  (reduce-kv (fn [a k v]
               (if (map? v)
                 (assoc a k (trim-content v))
                 (if (= k :content)
                   (assoc a k (reduce (fn [a x]
                                        (conj a (if (map? x)
                                                  (trim-content x)
                                                  (if (lvs? x)
                                                    (reduce (fn [aa xx]
                                                                   (conj aa (if (map? xx)
                                                                              (trim-content xx)
                                                                              (str/trim (str xx)))))
                                                                 []
                                                                 x)
                                                    (str/trim (str x))))))
                                      []
                                      (if (lvs? v) v [v])))
                   (assoc a k v))))
             nil
             m))

(declare cxml->map) ;; forward declaration to handle normalization of content with nested map values

(defn normalize-content
  "Normalizes content of converted XML tags."
  [xm & [tff]]
  (let [nc (reduce (fn [a x]
                     (if (string? x)
                       (assoc a tag-value-key (transform-field (:tag xm) x tff)) ;; (update a tag-value-key str x)
                       (update a (->kw (:tag x)) smart-conj (cxml->map x tff true))))
                   nil
                   (:content xm))]
    (if (and (= (count nc) 1) (get nc tag-value-key))
      (get nc tag-value-key)
      nc)
    nc))

(defn cxml->map
  "Converts standard Clojure XML conversion map to more idiomatic Clojure map with
  optional field transform function map tff and suppress outer XML tag flag.
  Sacrifices unassisted round-trip XML fidelity for more idiomatic Clojure map
  with minimal XML structure artifacts (attributes, content, et al)"
  [xm & [tff suppress-outer-tag? unsorted?]]
  (let [nc (normalize-content xm tff)
        mm (transform-map-fields (kebab-keys (:attrs xm)) tff)
        mmnc (merge nc mm)
        m (if suppress-outer-tag? mmnc
                                  {(->kw (:tag xm)) mmnc})]
    (if unsorted?
      m
      (into (sorted-map) m))))

(defn <-flatten-tag-value
  "Extracts tagged value (if any) from map m."
  [m]
  (when (and (map? m) (= (count m) 1))
    (get m tag-value-key)))

(declare flatten-tag-values) ;; forward declaration to handle tag value / map recursion

(defn flatten-tagged-map
  "Flattens tagged map m with intermediate aggregation from parsed Clojure map with tag values."
  [m]
  (reduce-kv (fn [a k v]
               (assoc a k (if-some [tvv (<-flatten-tag-value v)]
                            tvv
                            (flatten-tag-values v))))
             nil
             m))

(defn flatten-tag-values
  "Flattens tagged values from intermediate aggregation map from parse Clojure map with tag values."
  [x]
  (if (map? x)
    (if-some [tvv (<-flatten-tag-value x)]
      tvv
      (flatten-tagged-map x))
    (if (lvs? x)
      (reduce (fn [a xx] (conjv a (flatten-tag-values xx)))
              nil
              x)
      x)))

(defn normalize-keys-to-transform-funcs
  "Converts a transform map m with keys of transform function and
  keys of lists of associated field keys to new map with
  keys of field keys and value of associated transform function."
  [m]
  (reduce-kv (fn [a k v]
               (reduce (fn [aa xx] (assoc aa xx k))
                       a
                       v))
             nil
             m))

(declare transform-values) ;; forward declaration to handle nested maps for transform function applications

(defn transform-value
  "Transforms the key-value k v pair given the transform function map tfm.
  tfm: key=k value=transform-function"
  [tfm k v]
  (if (map? v)
    (transform-values tfm v)
    (if (lvs? v)
      (reduce (fn [a x] (conjv a (transform-value tfm k x)))
              nil
              v)
      (try
        (apply (get tfm k identity) [v])
        (catch Exception e
          v)))))

(defn transform-values
  "Transform the values of map m given a transform function map tfm.
  tfm: key=map-key value=transform-function"
  [tfm m]
  (reduce-kv (fn [a k v]
               (assoc a k (if (map? v)
                            (transform-values tfm v)
                            (transform-value tfm k v))))
             nil
             m))

(defn xml->normalized-map
  "Converts an XML string xml-str to a simplified and more idiomatic Clojure map with
  optional arguments to transform function map tfm and flag to
  suppress outer tag."
  [xml-str & [tfm suppress-outer-tag?]]
  (try
    (when-let [cmap (->cxml xml-str)]
      (when-let [xm (cxml->map cmap tfm suppress-outer-tag?)]
        (flatten-tagged-map xm)))
    (catch Exception e
      (throw e))))

(defn xml->map
  "Converts an XML string xml-str to a simplified and more idiomatic Clojure map with
  optional arguments to transform function map tfm and flag to
  suppress outer tag."
  [xml-str & [tfm suppress-outer-tag?]]
  (xml->normalized-map xml-str tfm suppress-outer-tag?))

(defn map->xml
  "Converts a map m to an XML string with an outer tag outer-tag and
  the optional arguments of an attribute map attr-map."
  [m & [attr-map outer-tag]]
  (->xml-str (cxml->attrs-cxml (map->cxml m nil outer-tag) attr-map)))
