package io.quarkiverse.cxf.it.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class Java2wsTest {

    @Test
    public void java2WsHelloService() throws IOException, ParserConfigurationException, SAXException, TransformerException {
        java2Ws("hello", "HelloServiceImpl");
    }

    @Test
    public void java2WsFaultyHelloService()
            throws IOException, ParserConfigurationException, SAXException, TransformerException {
        java2Ws("faulty-hello", "FaultyHelloServiceImpl");
    }

    public void java2Ws(String path, String serviceName)
            throws IOException, ParserConfigurationException, SAXException, TransformerException {
        String servedWsdl = RestAssured.given()
                .get("/soap/" + path + "?wsdl")
                .then()
                .statusCode(200)
                .extract().body().asString();
        servedWsdl = normalizeNsPrefixes(servedWsdl);
        Path generatedPath = Paths.get("target/Java2wsTest/" + serviceName + "-from-java2ws.wsdl");
        try (InputStream in = Files.newInputStream(generatedPath)) {
            final String java2WsGeneratedWsdl = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            /* We have to compare on DOM level so that different order of XML attributes, etc. does not make the test fail */
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setCoalescing(true);
            dbf.setIgnoringElementContentWhitespace(true);
            dbf.setIgnoringComments(true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            final Document generatedDoc = parse(db, java2WsGeneratedWsdl);
            final Document servedDoc = parse(db, servedWsdl);

            boolean equal = generatedDoc.isEqualNode(servedDoc);
            String mode = this.getClass().getSimpleName().endsWith("IT") ? "native" : "jvm";
            Path servedPath = Paths.get("target/Java2wsTest/" + serviceName + "-served-normalized-" + mode + ".wsdl")
                    .toAbsolutePath();
            Path generatedNormalizedPath = Paths.get("target/Java2wsTest/" + serviceName + "-from-java2ws-normalized.wsdl")
                    .toAbsolutePath();
            save(servedDoc, servedPath);
            save(generatedDoc, generatedNormalizedPath);
            if (!equal) {
                Assertions.fail(
                        "The WSDL generated by java2ws and the WSDL served by the application are not equal XML documents. You may want to compare "
                                + generatedNormalizedPath + " vs. " + servedPath);
            }

        }
    }

    protected String normalizeNsPrefixes(String servedWsdl) {
        /*
         * ns1 does not seem to be used anywhere in the WSDL document so it should be fine to remove it.
         * At the same time it is the only difference against the document produced by java2ws which is also fine
         */
        return servedWsdl.replace("xmlns:ns1=\"http://schemas.xmlsoap.org/soap/http\"", "");
    }

    static void save(Document doc, Path path) throws TransformerException, IOException {
        Files.createDirectories(path.getParent());
        Transformer t = TransformerFactory.newDefaultInstance().newTransformer();
        t.transform(new DOMSource(doc), new StreamResult(path.toFile()));
    }

    static Document parse(DocumentBuilder db, String wsdlDoc) throws SAXException, IOException {
        Document doc = db.parse(new InputSource(new StringReader(wsdlDoc)));

        /*
         * There is some default :9090 location in the generated WSDL so we remove the whole address node from both
         */
        NodeList adrNodes = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "address");
        List<Node> adrNodesList = new ArrayList<>();
        for (int i = 0; i < adrNodes.getLength(); i++) {
            adrNodesList.add(adrNodes.item(i));
        }
        for (Node node : adrNodesList) {
            node.getParentNode().removeChild(node);
        }
        doc.normalizeDocument();
        return doc;
    }

}
