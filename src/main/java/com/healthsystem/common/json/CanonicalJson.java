package com.healthsystem.common.json;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

public final class CanonicalJson {
 private CanonicalJson() {}
 public static String hash(JsonNode node) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text(node).getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String text(JsonNode node) {
  if(node==null||node.isNull()||node.isMissingNode())return "null";
  if(node.isObject()) {
   var names=new TreeSet<>(node.propertyNames());var parts=new ArrayList<String>();
   for(String name:names)parts.add(JsonValues.write(name)+":"+text(node.get(name)));
   return "{"+String.join(",",parts)+"}";
  }
  if(node.isArray()) {var parts=new ArrayList<String>();for(var value:node)parts.add(text(value));return "["+String.join(",",parts)+"]";}
  if(node.isNumber())return node.decimalValue().stripTrailingZeros().toPlainString();
  return node.toString();
 }
}
