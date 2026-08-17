# kitfiscal-client — SDK Java

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/ro.kitfiscal/kitfiscal-client.svg)](https://central.sonatype.com/artifact/ro.kitfiscal/kitfiscal-client)

Client Java pentru `kitfiscal API` (estimator fiscal PFA / SRL Micro / SRL
Profit, România 2026). Fără dependențe de runtime — folosește
`java.net.http.HttpClient` (standard din JDK 11+) și un parser JSON propriu,
minimal. Potrivit pentru integrare directă în Spring Boot, Quarkus sau orice
serviciu Java/Kotlin enterprise, fără riscul unui conflict de versiuni cu
Jackson/Gson/OkHttp deja prezente în aplicația gazdă.

## Instalare (Maven)

```xml
<dependency>
    <groupId>ro.kitfiscal</groupId>
    <artifactId>kitfiscal-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Necesită Java 17+ (pentru `record`-uri și pattern matching pe `switch`).

## Utilizare

```java
import ro.kitfiscal.client.KitFiscalClient;
import ro.kitfiscal.client.model.RezultatFiscal;
import ro.kitfiscal.client.model.VenitInput;

KitFiscalClient client = new KitFiscalClient("https://api.exemplu.ro/kitfiscal/");

RezultatFiscal rezultat = client.pfa(VenitInput.of(250_000, 60_000));
System.out.println(rezultat.formaJuridica() + ": " + rezultat.venitNetRamas() + " lei net");
```

Comparație între toate formele juridice:

```java
RezultatComparativ comparativ = client.compara(VenitInput.of(300_000, 50_000));
System.out.println("Cel mai avantajos: " + comparativ.celMaiFavorabil());
```

Configurare completă (cheie API, timeout, retry):

```java
KitFiscalClient client = new KitFiscalClient(
    "https://api.exemplu.ro/kitfiscal/",
    "cheia-mea-api",
    Duration.ofSeconds(10),
    3,      // maxRetries
    null    // transport = null => HttpClient real
);
```

## Gestionarea erorilor

Orice răspuns HTTP 4xx/5xx sau JSON invalid aruncă
`ro.kitfiscal.client.exception.KitFiscalApiException` (unchecked), cu
`getStatusCode()` și `getResponseBody()` disponibile pentru diagnoză. Erorile
5xx sunt reîncercate automat (backoff simplu); erorile 4xx (validare) nu sunt
reîncercate.

```java
try {
    RezultatFiscal rezultat = client.pfa(input);
} catch (KitFiscalApiException e) {
    log.error("kitfiscal API a eșuat: {} (status {})", e.getMessage(), e.getStatusCode());
}
```

## De ce fără Jackson/Gson

Suprafața API-ului kitfiscal e mică, stabilă și fără polimorfism (obiecte
plate). Un parser JSON extern ar aduce o dependență transitivă suplimentară
exact în zona (serializare JSON) unde aplicațiile enterprise au deja o
alegere fixată — și des vin conflicte de versiune. Parserul intern din
`ro.kitfiscal.client.internal.Json` acoperă exact ce produce FastAPI/Pydantic
în răspunsurile acestui serviciu; nu e un parser JSON complet-conform (nu
tratează toate cazurile exotice de escaping Unicode), dar e suficient aici.

## Testare

```bash
mvn test
```

Testele (JUnit 5) nu ating rețeaua reală — folosesc interfața
`KitFiscalClient.Transport` pentru a injecta răspunsuri HTTP simulate direct
în constructorul clientului.

> Notă privind verificarea locală a acestui SDK: în mediul în care a fost
> scris acest cod, Maven Central nu era accesibil, deci testele JUnit 5 nu au
> putut fi rulate direct cu `mvn test`. În schimb, codul sursă a fost
> compilat cu `javac` (fără erori) și verificat printr-un smoke-test manual
> care a pornit un server `kitfiscal API` real și a apelat efectiv `health`,
> `pfa` și `compara` prin HTTP — acest test a scos la iveală și a permis
> corectarea unui bug real (vezi mai jos). Testele JUnit 5 din `src/test`
> rulează normal într-un mediu CI cu acces la Maven Central.

## Bug cunoscut, deja corectat

Versiunea inițială folosea `HttpClient` cu negocierea implicită de protocol,
care încearcă un upgrade la HTTP/2 (h2c) înainte de a trimite cererea.
Împotriva unui server HTTP/1.1 strict (precum `uvicorn`), acest lucru putea
duce la cereri `POST` al căror corp JSON nu ajungea la server, rezultând în
erori `422` de validare pe partea de API, deși payload-ul construit local era
corect. Clientul forțează acum explicit `HttpClient.Version.HTTP_1_1`.

## Publicare pe Maven Central

Automată, prin `.github/workflows/publish-java-sdk.yml` — se declanșează la
un tag Git de forma `java-v1.0.0`. Necesită, configurate o singură dată în
Settings → Secrets ale acestui repository: cont Sonatype verificat pentru
namespace-ul `ro.kitfiscal`, plus o cheie GPG pentru semnarea artefactelor
(Maven Central respinge orice pachet nesemnat). Detalii complete, în
comentariile din capul fișierului workflow.

```bash
git tag java-v1.0.0
git push origin java-v1.0.0
```
