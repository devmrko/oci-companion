package io.github.devmrko.companion.service;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** Bounded, non-extracting JUnit/PMD reader. Missing evidence is never a passing test. */
public final class TestReports {
    public record Failure(String suite,String test,String message) {}
    public record Finding(String file,String line,String rule,String priority,String message) {}
    public record PmdReport(String path,String version,int violations,int errors,int suppressed,List<Finding> findings,List<String> problems,String xml) {}
    public record Summary(String state,int files,int tests,int failures,int errors,int skipped,List<Failure> details,List<PmdReport> pmd) {
        public Summary(String state,int files,int tests,int failures,int errors,int skipped,List<Failure> details) {this(state,files,tests,failures,errors,skipped,details,List.of());}
    }
    private static final class LimitedInput extends FilterInputStream {
        private long remaining=64*1024*1024L;
        LimitedInput(InputStream input){super(input);}
        public int read() throws IOException {if(remaining<=0)throw new IOException("보고서 크기 제한 초과");int value=super.read();if(value>=0)remaining--;return value;}
        public int read(byte[] bytes,int offset,int length) throws IOException {if(remaining<=0)throw new IOException("보고서 크기 제한 초과");int count=in.read(bytes,offset,(int)Math.min(length,remaining));if(count>0)remaining-=count;return count;}
        public long skip(long count) throws IOException {byte[] buffer=new byte[8192];long total=0;while(total<count){int read=read(buffer,0,(int)Math.min(buffer.length,count-total));if(read<0)break;total+=read;}return total;}
    }
    public static Summary missing(){return new Summary("MISSING",0,0,0,0,0,List.of());}
    public static Summary read(InputStream input) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);factory.setNamespaceAware(true);
        int files=0,tests=0,failures=0,errors=0,skipped=0,entries=0;long total=0;
        var details=new ArrayList<Failure>();
        var pmd=new ArrayList<PmdReport>();
        try(var tar=new TarArchiveInputStream(new LimitedInput(new GZIPInputStream(input)))) {
            for(var entry=tar.getNextEntry();entry!=null;entry=tar.getNextEntry()) {
                if(++entries>10000 || entry.getSize()<0 || (total+=entry.getSize())>64*1024*1024L)throw new IOException("보고서 크기 제한 초과");
                if(!entry.isFile() || !entry.getName().endsWith(".xml") || entry.getName().contains("/__MACOSX/") || entry.getName().substring(entry.getName().lastIndexOf('/')+1).startsWith("._"))continue;
                if(entry.getSize()>4*1024*1024)throw new IOException("XML 크기 제한 초과");
                byte[] content=tar.readNBytes((int)entry.getSize());
                var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
                if("pmd".equals(document.getDocumentElement().getLocalName())) {
                    var findings=new ArrayList<Finding>();var problems=new ArrayList<String>();
                    var violations=document.getElementsByTagNameNS("*","violation");
                    for(int i=0;i<Math.min(500,violations.getLength());i++) {
                        var v=(org.w3c.dom.Element)violations.item(i);
                        String file=v.getParentNode() instanceof org.w3c.dom.Element parent?parent.getAttribute("name"):"";
                        findings.add(new Finding(file,v.getAttribute("beginline"),v.getAttribute("rule"),v.getAttribute("priority"),v.getTextContent().strip()));
                    }
                    int problemCount=0;
                    for(String tag:List.of("error","configerror")) {
                        var nodes=document.getElementsByTagNameNS("*",tag);problemCount+=nodes.getLength();
                        for(int i=0;i<nodes.getLength()&&problems.size()<100;i++) {
                            var e=(org.w3c.dom.Element)nodes.item(i);
                            problems.add((e.getAttribute("filename")+" "+e.getAttribute("rule")+" "+e.getAttribute("msg")+" "+e.getTextContent()).strip());
                        }
                    }
                    pmd.add(new PmdReport(entry.getName(),document.getDocumentElement().getAttribute("version"),violations.getLength(),problemCount,
                        document.getElementsByTagNameNS("*","suppressedviolation").getLength(),List.copyOf(findings),List.copyOf(problems),new String(content,java.nio.charset.StandardCharsets.UTF_8)));
                    continue;
                }
                String root=document.getDocumentElement().getTagName();
                if(!root.equals("testsuite") && !root.equals("testsuites"))continue;
                files++;var cases=document.getElementsByTagName("testcase");
                for(int i=0;i<cases.getLength();i++) {
                    var test=(org.w3c.dom.Element)cases.item(i);tests++;
                    boolean failed=false,error=false,skip=false;String message="";
                    for(var child=test.getFirstChild();child!=null;child=child.getNextSibling()) {
                        switch(child.getNodeName()) {
                            case "failure" -> {failed=true;message=child.getTextContent();}
                            case "error" -> {error=true;message=child.getTextContent();}
                            case "skipped" -> skip=true;
                        }
                    }
                    if(error)errors++;else if(failed)failures++;else if(skip)skipped++;
                    if((failed||error)&&details.size()<100)details.add(new Failure(test.getAttribute("classname"),test.getAttribute("name"),message.substring(0,Math.min(message.length(),2000))));
                }
            }
        }
        return new Summary(files==0?"MISSING":tests==0?"EMPTY":"AVAILABLE",files,tests,failures,errors,skipped,List.copyOf(details),List.copyOf(pmd));
    }
}
