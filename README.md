# xander

Xander is a Clojure library that provides convenience functions for handling XML as a more idiomatic 
map, conversion and translation options, and generally a more idiomatic manner in which to process XML.

## Rationale

Xander provides the ability to convert XML documents into elegant, idiomatic Clojure maps that
allows for more idiomatic processing of incoming messages.

The core Clojure parsing of XML provides fidelity of XML documents, but at the 
expense of readability and elegance.  

There are times that the incoming XML documents don't need to maintain the fidelity of
attributes and child tag contents and that is where xander may be of use.
  
## Usage

In `project.clj` and the `:dependencies` vector:

```clojure
[xander "0.1.2"]
```

## Build Status

<img src="https://circleci.com/gh/sierralogic/xander.png?style=shield&circle-token=208a5b34334d277791f8e046d563216d320e7343"/>

## API Documentation

Xander API documentation may be found [here](https://sierralogic.github.io/xander/doc/xander.core.html).

## Examples

### Convert XML string to simple, idiomatic, aggregated, normalized map

```clojure
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
    
;; default optional parameters transform function map (nil) and supporess outer tag (falsse) so don't suppress outer tag 'foo'    
(xml->map fruit-xml-str)
;;=>
{:foo {:bar "baz",
       :fruit {:apple {:type [{:color "red", :id "fuji", :label "Fuji", :taste "sweet"}
                              {:color "green", :id "granny", :label "Granny Smith", :taste "sour"}]}},
       :ans "42",
       :poseur "true",
       :spice "pumpkin"}}

;; suppress outer tag 'foo'
;; note that even though the outer tag 'foo' is suppressed, it's attributes ('pumpkin') are 
;; included on the first level of the resulting map.  all attributes of the outer tag if suppressed
;; will appear in the first tier of the resulting map.
(xml->map fruit-xml-str nil true)
;;=>
{:ans "42",
 :bar "baz",
 :fruit {:apple {:type [{:color "red", :id "fuji", :label "Fuji", :taste "sweet"}
                        {:color "green", :id "granny", :label "Granny Smith", :taste "sour"}]}},
 :poseur "true",
 :spice "pumpkin"}
```

### Transform fields on conversion using transform field maps

Continuing from the example code above:

```clojure
;; note: for production (or anything significant) you should have more resilient functions
;; that handle exceptions since the conversion will throw an exception and abort the conversion
(def tfm-fruit {:id keyword
                :color keyword
                :poseur #(or (= % "true") (= % "t") (= % "yes") (= % "y") (= % "si"))
                :label str/upper-case
                :spice #(if (= % "pumpkin") "Beware! The Great Pumpkin!" %)
                :ans #(Integer/parseInt %)})

(xml->map fruit-xml-str tfm-fruit true)
;;=>
{:ans 42,
 :bar "baz",
 :fruit {:apple {:type [{:color :red, :id :fuji, :label "FUJI", :taste "sweet"}
                        {:color :green, :id :granny, :label "GRANNY SMITH", :taste "sour"}]}},
 :poseur true,
 :spice "Beware! The Great Pumpkin!"}
 
(xml->map fruit-xml-str tfm-fruit false)
;; equivalent to: (xml->map fruit-xml-str tfm-fruit)
;;=>
{:foo {:bar "baz",
       :fruit {:apple {:type [{:color :red, :id :fuji, :label "FUJI", :taste "sweet"}
                              {:color :green, :id :granny, :label "GRANNY SMITH", :taste "sour"}]}},
       :ans 42,
       :poseur true,
       :spice "Beware! The Great Pumpkin!"}}
```

## Round Tripping (XML -> Clojure maps -> XML)

The fact that XML has both child tags and attributes in which to represent associated fields to a `tag` makes it
impossible to round-trip XML to Clojure maps *without* some sort of mapping of what values goes where from
the Clojure representation to the XML representation.

This is the reason the result of Clojure library function to parse XML strings has the shape
that it does to allow for round-tripping without losing any shape fidelity in the original 
XML document.

Xander allows for round-tripping in most cases, providing the conversion *back* to XML string
has an attribute mapping to extract the attributes and place them correctly in their
parent tags.

Note that if there are any field transformations on the incoming XML then those field values
will remain transformed *AND* if you want to roundtrip, you should suppress the outer tag
when doing the `(xml->map xml-str transform-field-map true)` where the `true` flag suppresses
the outer tag on conversion.  

```clojure
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

(def fruit->xml {:foo {xander/attrs-key [:spice]
                       :fruit {:apple {:type {xander/attrs-key [:id :label]}}}}})

;; roundtripping works with the output being the same shaped XML string input (minus some minor whitespace differences)
(map->xml (xml->map fruit-xml-str) fruit->xml)
;;=>
"<?xml version='1.0' encoding='UTF-8'?><foo spice=\"pumpkin\">
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
</foo>"
```

### Simple map to xml

```clojure
(def fruit-map (xml->map rt-xml-str tfm-fruit))
;;=>
{:foo {:ans 42,
       :bar "baz",
       :fruit {:apple {:type [{:color :red, :id :fuji, :label "FUJI", :taste "sweet"}
                              {:color :green, :id :granny, :label "GRANNY SMITH", :taste "sour"}]}},
       :poseur true,
       :spice "Beware! The Great Pumpkin!"}}

(map->xml fruit-map)
;;=>
"<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>
   <ans>42</ans>
   <bar>baz</bar>
   <fruit>
     <apple>
       <type>
         <color>red</color>
         <id>fuji</id>
         <label>FUJI</label>
         <taste>sweet</taste>
       </type>
       <type>
         <color>green</color>
         <id>granny</id>
         <label>GRANNY SMITH</label>
         <taste>sour</taste>
       </type>
     </apple>
   </fruit>
   <poseur>true</poseur>
   <spice>Beware! The Great Pumpkin!</spice>
 </foo>
 "
 
(def fruit->xml {:foo {:_attrs [:spice], 
                       :fruit {:apple {:type {:_attrs [:id :label]}}}}})

(map->xml fruit-map fruit->xml)
;;=>
"<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo spice=\"Beware! The Great Pumpkin!\">
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
 "
```

## Field Transform Functions

Field transform function maps have the field name as the key, and the value
is a function that takes a single argument (1-arity) and returns the transformed
value.

```clojure
(def tfm {:ans #(Integer/parseInt %)})

(def lucky-xml-str
  "<?xml version='1.0' encoding='UTF-8'?>
    <foo>
      <ans>42</ans>
      <lucky>7</lucky>
      <lucky>11</lucky>
    </foo>")

(xml->map lucky-xml-str nil true)
;=>
{:ans "42", :lucky ["7" "11"]}

(xml->map lucky-xml-str tfm true)
;;=>
{:ans 42, :lucky ["7" "11"]}

(xml->map lucky-xml-str tfm)
;;=>
{:foo {:ans 42, :lucky ["7" "11"]}}
```

This works.  But what if you have multiple fields (like the additional `:lucky` field) that you want to convert to integer?
Do you have to put an entry for each field name with the integer conversion function as
the value?

```clojure
(def bloated-tfm {:ans #(Integer/parseInt %)
                  :lucky #(Integer/parseInt %)})
                  
(xml->map lucky-xml-str bloated-tfm true)
;;=> 
{:ans 42, :lucky [7 11]}
```

Xander provides a convenience function `generate-transform-field-funcs` to make it easier
to add fields to transforms functions using a simplified data structure.

```clojure
(def tff {#(Integer/parseInt %) [:ans :lucky]})
(def tfm-lucky (generate-transform-field-funcs tff))
;;=>
{:ans #object[xander.core_test$fn__2363 0x78155d80 "xander.core_test$fn__2363@78155d80"],
 :lucky #object[xander.core_test$fn__2363 0x78155d80 "xander.core_test$fn__2363@78155d80"]}

(xml->map lucky-xml-str tfm-lucky true)
;;=> 
{:ans 42, :lucky [7 11]}
```

Why would I want to do that?

In larger implementations, it's easier to key on the transform function (and just
add field names as needed) than to constantly add the same function reference for 
each individual field name key.

As stated, the `generated-transform-field-funcs` is a *convenience* function so you 
don't have to use it and may use direct transform function map declarations.

## Future Enhancements

* Handle attribute/child tag collisions better (now attribute wins destructively).
* Provide mechanism for namespacing field names and other manipulations on conversion to map.
* Determine and fix any stability and/or performance issues. (on-going)
* Create more unit tests, including generative testing. (on-going)

## License

Copyright © 2017 SierraLogic LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
