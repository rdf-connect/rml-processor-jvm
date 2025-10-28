# RmlMapper Processor for RDF Connect

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

### `Source`

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

A logical source MUST define the following three parameters:

* Type — The type MUST be `rdfc:Source`.
* Reader — The property `rdfc:reader` MUST map either to the channel identifier or to the value of the `rdfc:mappingId` property.
* Mime Type — The property `rdfc:mimeType` MUST specify the MIME type of the incoming data.


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
      rdfc:format "application/json"; 
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

## Development

### Build Instructions

To build the processor jar that includes the processor and its descriptor, use the Shadow plugin to produce a fat JAR using the following Gradle command:

```bash
gradle shadowJar
```




