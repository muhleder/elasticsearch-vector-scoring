# Fast Elasticsearch Vector Scoring

This Plugin allows you to score Elasticsearch documents based on embedding-vectors, using dot-product or cosine-similarity.

## General
* Updated version for ES 6.1 of [this plugin](https://github.com/lior-k/fast-elasticsearch-vector-scoring)
* This plugin was inspired from [This elasticsearch vector scoring plugin](https://github.com/MLnick/elasticsearch-vector-scoring) and [this discussion](https://discuss.elastic.co/t/vector-scoring/85227/6) to achieve 10 times faster processing over the original.
* lior-k gained this substantial speed improvement by using the lucene index directly
* lior-k developed it for their workplace which needs to pick KNN from a set of ~4M vectors. Their current ES setup is able to answer this in ~80ms
* Cosine support
* Vector norms can be stored with vector and can be used when cosine distance calculating, so that they are not recalculated for each request


## Elasticsearch version
* Currently designed for Elasticsearch 6.3.1.


## Setup

In order to install this plugin, you need to create a zip distribution first by running

```bash
gradle clean assemble
```

This will produce a zip file in `build/distributions`.

After building the zip file, you can install it like this

```bash
elasticsearch-plugin install file:///path/to/iplugin/build/distribution/FILENAME.zip
```


 
## Debugging
Place this into an elasticsearch checkout, add the plugin to the projects list in `/settings.gradle` and run 

    gradle :plugins:vector-scoring:run --debug-jvm



## Usage

### Documents
* Each document you score should have a field containing the base64 representation of your vector. for example vector:
```
[-0.09950172156095505, 0.07625244557857513, 0.017503950744867325, -0.14847373962402344, 0.1895207166671753, -0.19025510549545288, -0.10633774101734161, -0.12354782223701477, -0.06308252364397049, 0.11947114765644073, -0.0653892382979393, -0.4654960334300995, 0.057657890021800995, -0.08209892362356186, -0.03890344500541687, 0.054604820907115936, -0.0035240077413618565, -0.06045947223901749, 0.011299720034003258, -0.043770890682935715, 0.02771991491317749, 0.02623981609940529, -0.04990408569574356, 0.06557474285364151, 0.021160271018743515, 0.0531679168343544, 0.11786060035228729, 0.015350733883678913, -0.12084735184907913, 0.034496061503887177, 0.008482367731630802, 0.0433405302464962, -0.05225555971264839, -0.040460359305143356, -0.008764605969190598, 0.19139364361763, 0.02501053921878338, -0.16797123849391937, -0.08835361897945404, 0.10550480335950851, 0.04281047359108925, 0.0034949961118400097, -0.020001886412501335, -0.04299351945519447, -0.04794740304350853, 0.0029372263234108686, 0.4430026113986969, -0.18841710686683655, -0.051676105707883835, 0.08963997662067413, 0.19411885738372803, 0.004212886560708284, 0.1271815448999405, -0.006043014116585255, 0.0027108797803521156, 0.05948426574468613, -0.0038672189693897963, -0.0012325347634032369, -0.3746754825115204, -0.03635839372873306, -0.0879824087023735, 0.02211793325841427, 0.03946676850318909, 0.11700475960969925]

```
should be converted to:
```
{
    "id": 1,
    ....
    "content_vector": "v7l48eAAAAA/s4VHwAAAAD+R7I5AAAAAv8MBMAAAAAA/yEI3AAAAAL/IWkeAAAAAv7s480AAAAC/v6DUgAAAAL+wJi0gAAAAP76VqUAAAAC/sL1ZYAAAAL/dyq/gAAAAP62FVcAAAAC/tQRvYAAAAL+j6ycAAAAAP6v1KcAAAAC/bN5hQAAAAL+u9ItAAAAAP4ckTsAAAAC/pmkjYAAAAD+cYpwAAAAAP5renEAAAAC/qY0HQAAAAD+wyYGgAAAAP5WrCcAAAAA/qzjTQAAAAD++LBzAAAAAP49wNKAAAAC/vu/aIAAAAD+hqXfAAAAAP4FfNCAAAAA/pjC64AAAAL+qwT2gAAAAv6S3OGAAAAC/gfMtgAAAAD/If5ZAAAAAP5mcXOAAAAC/xYAU4AAAAL+2nlfAAAAAP7sCXOAAAAA/petBIAAAAD9soYnAAAAAv5R7X+AAAAC/pgM/IAAAAL+ojI/gAAAAP2gPz2AAAAA/3FonoAAAAL/IHg1AAAAAv6p1SmAAAAA/tvKlQAAAAD/I2OMAAAAAP3FBiCAAAAA/wEd8IAAAAL94wI9AAAAAP2Y1IIAAAAA/rnS4wAAAAL9vriVgAAAAv1QxoCAAAAC/1/qu4AAAAL+inZFAAAAAv7aGA+AAAAA/lqYVYAAAAD+kNP0AAAAAP730BiAAAAA/8AAAAq9PCQ=="
}
```

Also in this example I saved vector norm as last element of array and convert vector and its norm to base64 representation

### Converting a vector to Base64

**Python**
```
import struct
import base64
import math


def calc_vector_norm(vector):
    norm = 0
    for elem in vector:
        norm += elem**2
    return math.sqrt(norm)


def encode_v(vector):
    vector.append(calc_vector_norm(vector))
    return base64.b64encode(
        struct.pack('>%sd' % len(vector), *vector)
    ).decode('utf-8')
```

* Use this field mapping:
```
PUT my_index
{
  "mappings": {
    "doc": {
      "properties": {
        "embedding_vector": {
          "type": "binary",
          "doc_values": true
        }
      }
    }
  }
}
```
* The vector can be of any dimension

### Querying
* For querying the 100 KNN documents use this POST message on your ES index:
 
```
POST /_search
{
  "query": {
    "function_score": {
      "query": {
        "match": {
          "name": "Doe"
        }
      },
      "functions": [
        {
          "script_score": {
            "script": {
              "source": "vector_scoring",
              "lang": "binary_vector_score",
              "params": {
                "use_stored_vector_norm": true,
                "cosine": true,
                "vector_field": "content_vector",
                "vector": [
                  -0.09217305481433868,
                  0.010635560378432274,
                  -0.02878434956073761,
                  0.06988169997930527,
                  0.1273992955684662,
                  -0.023723633959889412,
                  0.05490724742412567,
                  -0.12124507874250412,
                  -0.023694118484854698,
                  0.014595639891922474,
                  0.1471538096666336,
                  0.044936809688806534,
                  -0.02795785665512085,
                  -0.05665992572903633,
                  -0.2441125512123108,
                  0.2755320072174072,
                  0.11451690644025803,
                  0.20242854952812195,
                  -0.1387604922056198,
                  0.05219579488039017,
                  0.1145530641078949,
                  0.09967200458049774,
                  0.2161576747894287,
                  0.06157230958342552,
                  0.10350126028060913,
                  0.20387393236160278,
                  0.1367097795009613,
                  0.02070528082549572,
                  0.19238869845867157,
                  0.059613026678562164,
                  0.014012521132826805,
                  0.16701748967170715,
                  0.04985826835036278,
                  -0.10990987718105316,
                  -0.12032567709684372,
                  -0.1450948715209961,
                  0.13585780560970306,
                  0.037511035799980164,
                  0.04251480475068092,
                  0.10693439096212387,
                  -0.08861573040485382,
                  -0.07457160204648972,
                  0.0549330934882164,
                  0.19136285781860352,
                  0.03346432000398636,
                  -0.03652812913060188,
                  -0.1902569830417633,
                  0.03250952064990997,
                  -0.3061246871948242,
                  0.05219300463795662,
                  -0.07879918068647385,
                  0.1403723508119583,
                  -0.08893408626317978,
                  -0.24330253899097443,
                  -0.07105310261249542,
                  -0.18161986768245697,
                  0.15501035749912262,
                  -0.216160386800766,
                  -0.06377710402011871,
                  -0.07671763002872467,
                  0.05360138416290283,
                  -0.052845533937215805,
                  -0.02905619889497757,
                  0.08279753476381302
                ]
              }
            }
          }
        }
      ]
    }
  }
}
```
* The example above shows a vector of 64 dimensions
* Parameters:
   1. `field_vector`: The field containing the base64 vector.
   2. `vector`: The vector (comma separated) to compare to.
   3. `cosine`: (boolean) calculate cosine distance or dot product of vectors
   4. `use_stored_vector_norm`: (boolean) if `true` stored norm will be used when cosine distance will calculating

