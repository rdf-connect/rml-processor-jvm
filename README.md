# RmlMapper Processor for RDF Connect

[![Unit Tests](https://github.com/rdf-connect/rml-processor-jvm/actions/workflows/unit-tests.yml/badge.svg)](https://github.com/rdf-connect/rml-processor-jvm/actions/workflows/unit-tests.yml)
[![](https://jitpack.io/v/RDF-Connect/rml-processor-jvm.svg)](https://jitpack.io/#RDF-Connect/rml-processor-jvm)

`RmlMapper` is a processor for the RDF-Connect streaming pipeline framework.  
It allows you to map data from sources to targets using RML mappings, supporting multiple sources and targets as well as a default target.

## Usage in RDF-Connect

### Get the jar

Download the jar with, or install it with gradle.

**Download:**

```bash
wget 'jitpack.io/com/github/rdf-connect/rml-processor-jvm/master-SNAPSHOT/rml-processor-jvm-master-SNAPSHOT-all.jar'
```

**Gradle:**

Configure your `build.gradle` with JitPack and add the dependency

```gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }  // if your processors are on GitHub
}
dependencies {
    implementation("com.github.rdf-connect:rml-processor-jvm:master-SNAPSHOT:all")
}

tasks.register('copyPlugins', Copy) {
    from configurations.runtimeClasspath
    into "$buildDir/plugins"
}
```

Install the jar.
```
gradle copyPlugins
```


### Pipeline example 

```turtle
# Import the jvm-runner
<> owl:imports <https://javadoc.jitpack.io/com/github/rdf-connect/jvm-runner/runner/master-SNAPSHOT/runner-master-SNAPSHOT-index.jar>.

# Import the downloaded or installed jar
<> owl:imports <./rml-processor-jvm-master-SNAPSHOT-all.jar>.
# or
<> owl:imports <./build/plugins/rml-processor-jvm-master-SNAPSHOT-all.jar>.

# Link the mapper and the jvm runner in the pipeline
<> a rdfc:Pipeline;
  rdfc:consistsOf [
    rdfc:instantiates rdfc:JvmRunner;
    rdfc:processor <mapper>;
  ].

# Don't forget to defined <mapper>
```


## Overview

- Define structured mappings between input data sources and output targets.
- Supports optional triggers and default targets.


## Configuration

### `Source` (if any)

Each source must provide:

| Property     | Type          | Description                          | Required |
|-------------|---------------|---------------------------------------|----------|
| `rdfc:reader`    | `rdfc:Reader` | Component that reads data             | ✅       |
| `rdfc:mappingId` | `xsd:string`  | Optional identifier used in the mapping | ⚪       |
| `rdfc:triggers`  | `xsd:boolean` | Optional flag to trigger mapping      | ⚪       |


Note: at least one source should have `rdfc:triggers "true"`.

### `Target`

Each target must provide:

| Property  | Type          | Description                                        | Required |
|-----------|---------------|----------------------------------------------------|----------|
| `rdfc:writer`    | `rdfc:Writer` | Component that writes data to target        | ✅       |
| `rdfc:mappingId` | `xsd:string`  | Optional identifier used in the mapping     | ⚪       |
| `rdfc:format`    | `xsd:string`  | Optional output format ("turtle" or "trig") | ⚪       |

### `DefaultTarget`

| Property  | Type          | Description                                      | Required |
|-----------|---------------|--------------------------------------------------|----------|
| `rdfc:writer`  | `rdfc:Writer` | Writer for unmapped outputs                 | ✅       |
| `rdfc:format`  | `xsd:string`  | Optional output format ("turtle" or "trig") | ⚪       |


### RmlMapper Properties

| Property                   | Type           | Description                                              | Required |
|----------------------------|----------------|----------------------------------------------------------|----------|
| `rdfc:mappings`            | `rdfc:Reader`  | Mappings to apply                                        | ✅       |
| `rdfc:baseIRI`             | `xsd:string`   | Optional base IRI for resolving relative IRIs            | ⚪       |
| `rdfc:waitForMappingClose` | `xsd:boolean`  | Wait for mapping input completion before processing data | ⚪       |
| `rdfc:defaultTarget`       | `DefaultTarget`| Optional default target for unmapped outputs             | ⚪       |
| `rdfc:source`              | `Source`       | Input sources (multiple allowed)                         | ⚪       |
| `rdfc:target`              | `Target`       | Output targets (multiple allowed)                        | ⚪       |


### RML Mapping File

The mapping file should include RDF-Connect specific configuration for both the logical source and the logical target.


#### Logical Source

The processor has to decide, for every logical source in the mapping, *which* incoming
channel it should read. Both sides agree on a single **string key**:

* **Registration.** Every `rdfc:source` in the pipeline is registered under its
  `rdfc:mappingId` if it has one, and under the reader channel's own IRI if it does not.
* **Lookup.** The processor collects every `rml:source` on the logical source and walks
  them in order, returning the first one that resolves to a registered key.

Three shapes are recognised:

| `rml:source` shape          | key that is looked up      | mime type                       |
|-----------------------------|----------------------------|---------------------------------|
| node typed `rdfc:Source`    | value of `rdfc:reader`     | `rdfc:mimeType`, else `""`      |
| plain literal               | the literal's string value | `""` (nothing to derive it from)|
| node typed `dcat:Distribution` | value of `dcat:downloadURL` | `dcat:mediaType`, else `""`  |

The `rdfc:Source` form is the explicit one and the one to prefer when you write mappings
by hand. The other two exist so that generated mappings — see [Using YARRRML](#using-yarrrml)
below — work without post-processing.

If none of the sources resolves to a registered key, the mapping falls through to the
stock RML resolution: local files, remote URLs, databases, SPARQL endpoints. That is what
makes `rml:source "data.json"` keep working as an ordinary file read. The one exception is
`rdfc:Source`, which can only ever mean an RDF-Connect channel: an `rdfc:reader` that
matches nothing raises an error listing the keys that *are* registered.

```turtle
# explicit: read from the source registered as rdfc:source1
rml:source [
  a rdfc:Source;
  rdfc:reader rdfc:source1;
  rdfc:mimeType "application/json";
]
```

Note that `rdfc:mappingId rdfc:source1` registers the *expanded* IRI
`https://w3id.org/rdf-connect#source1`, so the mapping file has to expand its prefix the
same way. Using a plain string id on both sides (`rdfc:mappingId "students"` and
`rml:source "students"`) sidesteps that entirely.


#### Logical Target

A logical target MUST be defined as either the identifier of the channel, or the value of the `rdfc:mappingId` property.


## Compact Example

Example RML Mapper processor definition.
This reads `mapping.ttl` and `data.json`, both `rdfc:target1` and the default target write their data to `<default>`.
```turtle
<mappingData> a rdfc:Reader, rdfc:Writer.
<mappingReader> a rdfc:GlobRead;
  rdfc:glob <./mapping.ttl>;
  rdfc:output <mappingData>;
  rdfc:closeOnEnd "false".

<data> a rdfc:Reader, rdfc:Writer.
<dataReader> a rdfc:GlobRead;
  rdfc:glob <./data.json>;
  rdfc:output <data>;
  rdfc:closeOnEnd "false".

<default> a rdfc:Reader, rdfc:Writer.
<mapper> a rdfc:RmlMapper;
  rdfc:mappings <mappingData>;
  rdfc:source [
    rdfc:triggers true;
    rdfc:reader <data>;
    rdfc:mappingId rdfc:source1;
  ];
  rdfc:target [
    rdfc:writer <default>;
    rdfc:format "trig";
    rdfc:mappingId rdfc:target1;
  ];
  rdfc:defaultTarget [
    rdfc:writer <default>;
    rdfc:format "trig";
  ].
```

Example RML mapping file definition.
```turtle
<TriplesMap1> a rr:TriplesMap;
  rml:logicalSource [
    rml:source [
      a rdfc:Source;
      # get input from rdfc:source1
      # which is mapped with rdfc:mappingId to <data> in the pipeline
      rdfc:reader rdfc:source1;
      rdfc:mimeType "application/json";
    ];
    rml:referenceFormulation ql:JSONPath;
    rml:iterator "$.students[*]";
  ];
  rr:subjectMap [ rr:template "http://example.com/{Name}" ];
  rr:predicateObjectMap [
    rr:predicate foaf:name;
    rr:objectMap [
      rml:reference "Name";
      # write this triple to rdfc:target1
      # which is mapped with rdfc:mappingId to <default> in the pipeline
      rml:logicalTarget rdfc:target1;
    ];
  ].
```

## Using YARRRML

YARRRML mappings work without any post-processing: name the source in YARRRML whatever you
registered it as in the pipeline. Here is the full path a source name travels.

**1. What you write** — `mapping.yml`. The part before `~` is the source name; give it the
same value you use as `rdfc:mappingId`.

```yaml
prefixes:
  foaf: http://xmlns.com/foaf/0.1/

mappings:
  student:
    sources:
      - ['students~jsonpath', '$.students[*]']
    s: http://example.com/$(Name)
    po:
      - [foaf:name, $(Name)]
```

**2. What `yarrrml-parser -i mapping.yml -o mapping.ttl` emits.** It writes legacy RML
(`http://semweb.mmlab.be/ns/rml#`) and the source name lands as a plain literal:

```turtle
@prefix rr:   <http://www.w3.org/ns/r2rml#>.
@prefix rml:  <http://semweb.mmlab.be/ns/rml#>.
@prefix ql:   <http://semweb.mmlab.be/ns/ql#>.
@prefix foaf: <http://xmlns.com/foaf/0.1/>.

<#student_0> a rr:TriplesMap;
  rml:logicalSource [ a rml:LogicalSource;
    rml:source "students";                     # <- just a literal
    rml:iterator "$.students[*]";
    rml:referenceFormulation ql:JSONPath;
  ];
  rr:subjectMap [ rr:template "http://example.com/{Name}" ];
  rr:predicateObjectMap [
    rr:predicateMap [ rr:constant foaf:name ];
    rr:objectMap [ rml:reference "Name" ];
  ].
```

**3. What the processor sees.** Before mapping, the processor runs the rmlmapper's
`MappingConformer` over every mapping to bring legacy RML up to RML 2. That step rewrites a
*literal* `rml:source` into a `dcat:Distribution` blank node — this is not something you
wrote, and it is why the second and third rows of the table above exist:

```turtle
@prefix rml:  <http://w3id.org/rml/>.
@prefix dcat: <http://www.w3.org/ns/dcat#>.

<#student_0> rml:logicalSource [ a rml:LogicalSource;
    rml:source [ a dcat:Distribution, rml:Source;
      dcat:downloadURL "students";             # <- your source name, moved here
    ];
    rml:iterator "$.students[*]";
    rml:referenceFormulation rml:JSONPath;
  ];
  ...
```

The processor reads `dcat:downloadURL`, finds `students` among the registered sources, and
streams the channel into the mapping. Had you written the mapping directly in RML 2, the
conformer would have left `rml:source "students"` alone as a literal and the literal row of
the table would apply instead — either way the same name resolves.

**4. The pipeline side** — register the channel under that name:

```turtle
<data> a rdfc:Reader, rdfc:Writer.
<mapper> a rdfc:RmlMapper;
  rdfc:mappings <mappingData>;
  rdfc:source [
    rdfc:reader <data>;
    rdfc:mappingId "students";                 # <- matches the YARRRML source name
    rdfc:triggers true;
  ];
  rdfc:defaultTarget [ rdfc:writer <out>; rdfc:format "trig" ].
```

If the name does not match anything registered, the mapping falls through to plain RML
resolution and the rmlmapper looks for a *file* called `students`, failing with
`Cannot find students`. That error almost always means the YARRRML source name and the
`rdfc:mappingId` have drifted apart.

## Development

### Build Instructions

To build the processor jar that includes the processor and its descriptor, use the Shadow plugin to produce a fat JAR using the following Gradle command:

```bash
gradle shadowJar
```




