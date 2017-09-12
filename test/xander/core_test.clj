(ns xander.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [xander.core :refer :all]))

(def simple-xml-str
  "<?xml version='1.0' encoding='UTF-8'?>
    <foo>
      <bar>baz</bar>
      <ans>42</ans>
      <poseur>true</poseur>
    </foo>")

(def simple-xml-str-with-attrs
  "<?xml version='1.0' encoding='UTF-8'?>
    <foo spice=\"pumpkin\">
      <bar>baz</bar>
      <ans>42</ans>
      <poseur>true</poseur>
    </foo>")

(def simple-cxml
  {:tag :foo,
   :attrs nil,
   :content [{:tag :bar, :attrs nil, :content ["baz"]}
             {:tag :ans, :attrs nil, :content ["42"]}
             {:tag :poseur, :attrs nil, :content ["true"]}]})

(def simple-map {:foo :bar :ans 42 :tau 6.28 :poseur true
                 :nested {:food {:fruit ["cherry", "apple"]
                                 :meat ["lamb" "beef"]
                                 :buffet [{:cost 4.32}
                                          {:cost 5.55 :description "yum"}]}
                          :nid 123
                          :drink {:beer {:ale [:porter :stout]}}}})

(def simple-attrs {:document {attrs-key [:ans :poseur]
                              :foo {attrs-key [:ans :tau]}
                              :nested {attrs-key [:nid]
                                       :food {:buffet {attrs-key [:cost]}}}}})

(def expected-simple-map->cxml {:tag :document,
                                :attrs nil,
                                :content [{:tag :foo, :attrs nil, :content ["bar"]}
                                          {:tag :ans, :attrs nil, :content ["42"]}
                                          {:tag :tau, :attrs nil, :content ["6.28"]}
                                          {:tag :poseur, :attrs nil, :content ["true"]}
                                          {:tag :nested,
                                           :attrs nil,
                                           :content [{:tag :food,
                                                      :attrs nil,
                                                      :content [{:tag :fruit, :attrs nil, :content ["cherry"]}
                                                                {:tag :fruit, :attrs nil, :content ["apple"]}
                                                                {:tag :meat, :attrs nil, :content ["lamb"]}
                                                                {:tag :meat, :attrs nil, :content ["beef"]}
                                                                {:tag :buffet, :attrs nil, :content [{:tag :cost, :attrs nil, :content ["4.32"]}]}
                                                                {:tag :buffet,
                                                                 :attrs nil,
                                                                 :content [{:tag :cost, :attrs nil, :content ["5.55"]}
                                                                           {:tag :description, :attrs nil, :content ["yum"]}]}]}
                                                     {:tag :nid, :attrs nil, :content ["123"]}
                                                     {:tag :drink,
                                                      :attrs nil,
                                                      :content [{:tag :beer,
                                                                 :attrs nil,
                                                                 :content [{:tag :ale, :attrs nil, :content ["porter"]}
                                                                           {:tag :ale, :attrs nil, :content ["stout"]}]}]}]}]})

(def expected-simple-map-attr->cxml-attrs {:tag :document,
                                           :attrs {:ans "42", :poseur "true"},
                                           :content [{:tag :foo, :attrs nil, :content ["bar"]}
                                                     {:tag :tau, :attrs nil, :content ["6.28"]}
                                                     {:tag :nested,
                                                      :attrs {:nid "123"},
                                                      :content [{:tag :food,
                                                                 :attrs nil,
                                                                 :content [{:tag :fruit, :attrs nil, :content ["cherry"]}
                                                                           {:tag :fruit, :attrs nil, :content ["apple"]}
                                                                           {:tag :meat, :attrs nil, :content ["lamb"]}
                                                                           {:tag :meat, :attrs nil, :content ["beef"]}
                                                                           {:tag :buffet, :attrs {:cost "4.32"}, :content []}
                                                                           {:tag :buffet,
                                                                            :attrs {:cost "5.55"},
                                                                            :content [{:tag :description, :attrs nil, :content ["yum"]}]}]}
                                                                {:tag :drink,
                                                                 :attrs nil,
                                                                 :content [{:tag :beer,
                                                                            :attrs nil,
                                                                            :content [{:tag :ale, :attrs nil, :content ["porter"]}
                                                                                      {:tag :ale, :attrs nil, :content ["stout"]}]}]}]}]})

(deftest test-simple-xml-to-cxml
  (testing "simple xml to cxml"
    (is (= (->cxml simple-xml-str) simple-cxml))))

(deftest test-map->cxml
  (testing "testing map to cxml"
    (is (= (map->cxml simple-map nil :document) expected-simple-map->cxml))))

(deftest test-cxml-map->cxml-attrs
  (testing "testing cxml attrs transform"
    (is (= (cxml->attrs-cxml (map->cxml simple-map nil :document) simple-attrs) expected-simple-map-attr->cxml-attrs))))

(def fruit->xml {:foo {attrs-key [:spice]
                       :fruit {:apple {:type {attrs-key [:id :label]}}}}})

(def tfm-fruit {:id keyword
                :color keyword
                :poseur #(or (= % "true") (= % "t") (= % "yes") (= % "y") (= % "si"))
                :label str/upper-case
                :spice #(if (= % "pumpkin") "Beware! The Great Pumpkin!" %)
                :ans #(Integer/parseInt %)})

(def expected-raw-fruit-map {:foo {:ans "42",
                                   :bar "baz",
                                   :spice "pumpkin",
                                   :fruit {:apple {:type [{:color "red", :id "fuji", :label "Fuji", :taste "sweet"}
                                                          {:color "green", :id "granny", :label "Granny Smith", :taste "sour"}]}},
                                   :poseur "true"}})

(def expected-raw-fruit-map-suppress-outer-tag {:ans "42",
                                                :bar "baz",
                                                :fruit {:apple {:type [{:color "red", :id "fuji", :label "Fuji", :taste "sweet"}
                                                                       {:color "green", :id "granny", :label "Granny Smith", :taste "sour"}]}},
                                                :poseur "true",
                                                :spice "pumpkin"})

(def expected-transformed-fruit-map {:foo {:ans 42,
                                           :bar "baz",
                                           :spice "Beware! The Great Pumpkin!",
                                           :fruit {:apple {:type [{:color :red, :id :fuji, :label "FUJI", :taste "sweet"}
                                                                  {:color :green, :id :granny, :label "GRANNY SMITH", :taste "sour"}]}},
                                           :poseur true}})

(def expected-transformed-fruit-map-suppress-outer-tag {:ans 42,
                                                        :bar "baz",
                                                        :fruit {:apple {:type [{:color :red, :id :fuji, :label "FUJI", :taste "sweet"}
                                                                               {:color :green, :id :granny, :label "GRANNY SMITH", :taste "sour"}]}},
                                                        :poseur true,
                                                        :spice "Beware! The Great Pumpkin!"})

(def fruit-xml-str
  "<?xml version='1.0' encoding='UTF-8'?>
    <foo spice=\"pumpkin\">
      <bar>baz</bar>
      <fruit>
        <apple>
          <type id='fuji' label='Fuji'>
            <color>red</color>
            <taste>sweet</taste>
          </type>
          <type id='granny' label='Granny Smith'>
            <color>green</color>
            <taste>sour</taste>
          </type>
        </apple>
      </fruit>
      <ans>42</ans>
      <poseur>true</poseur>
    </foo>")

(deftest test-xml->map
  (testing "testing xml->map transforms"
    (is (= (xml->map fruit-xml-str) expected-raw-fruit-map))
    (is (= (xml->map fruit-xml-str nil true) expected-raw-fruit-map-suppress-outer-tag))
    (is (= (xml->map fruit-xml-str tfm-fruit) expected-transformed-fruit-map))
    (is (= (xml->map fruit-xml-str tfm-fruit true) expected-transformed-fruit-map-suppress-outer-tag))))

(defn ->flatten-str [x] (str/replace x #"\s" ""))
(defn flatten= [s1 s2] (= (->flatten-str s1) (->flatten-str s2)))

(def expected-xml-str-fruit-raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo spice=\"pumpkin\">
   <ans>42</ans>
   <bar>baz</bar>
   <fruit>
     <apple>
       <type id=\"fuji\" label=\"Fuji\">
         <color>red</color>
         <taste>sweet</taste>
       </type>
       <type id=\"granny\" label=\"Granny Smith\">
         <color>green</color>
         <taste>sour</taste>
       </type>
     </apple>
   </fruit>
   <poseur>true</poseur>
 </foo>
 ")

(def expected-xml-str-fruit-transformed "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo spice=\"Beware! The Great Pumpkin!\">
   <ans>42</ans>
   <bar>baz</bar>
   <fruit>
     <apple>
       <type id=\"fuji\" label=\"FUJI\">
         <color>red</color>
         <taste>sweet</taste>
       </type>
       <type id=\"granny\" label=\"GRANNY SMITH\">
         <color>green</color>
         <taste>sour</taste>
       </type>
     </apple>
   </fruit>
   <poseur>true</poseur>
 </foo>
 ")

(deftest test-map->xml
  (testing "testing map->xml transforms"
    (is (flatten= (map->xml expected-raw-fruit-map-suppress-outer-tag fruit->xml :foo) expected-xml-str-fruit-raw))
    (is (flatten= (map->xml expected-transformed-fruit-map-suppress-outer-tag fruit->xml :foo) expected-xml-str-fruit-transformed))))

(deftest test-roundtrip
  (testing "testing limited xander roundtrip functionality"
    (is (flatten= (map->xml (xml->map expected-xml-str-fruit-raw nil true) fruit->xml :foo)
                  expected-xml-str-fruit-raw))))

(def lucky-xml-str
  "<?xml version='1.0' encoding='UTF-8'?>
    <foo spice=\"pumpkin\">
      <ans>42</ans>
      <lucky>7</lucky>
      <lucky>11</lucky>
      <spice>cardamon</spice>
    </foo>")

(def tfkm {#(Integer/parseInt %) [:ans :lucky]
           ->kw [:spice]})
(def tfm-lucky (generate-transform-field-funcs tfkm))

(deftest test-overload-tf
  (testing "testing generate transform field functions"
    (is (= (:lucky (xml->map lucky-xml-str tfm-lucky true)) [7 11]))
    (is (= (:ans (xml->map lucky-xml-str tfm-lucky true)) 42))
    (is (= (get-in (xml->map lucky-xml-str tfm-lucky) [:foo :lucky]) [7 11]))
    (is (= (get-in (xml->map lucky-xml-str tfm-lucky) [:foo :ans]) 42))))
