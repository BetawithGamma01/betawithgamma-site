package com.betawithgamma.microstructure;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Device-local tamper-evident seal. Not an external timestamp or transparency anchor. */
public final class EvidenceSealer {
    private static final String ALIAS="microstructure_evidence_seal_v1";
    private EvidenceSealer() {}
    public record Verification(boolean ok,String reason,String inventorySha256){}

    public static JSONObject seal(Context c,File dir)throws Exception{
        KeyPair kp=keyPair();
        JSONArray files=new JSONArray();String canonical=inventory(dir,files,null);
        byte[] bytes=canonical.getBytes(StandardCharsets.UTF_8);String invSha=Hashing.sha256(bytes);
        java.security.Signature sig=java.security.Signature.getInstance("SHA256withECDSA");sig.initSign(kp.getPrivate());sig.update(bytes);byte[] signed=sig.sign();
        String pub=Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        JSONObject j=new JSONObject();j.put("schema","microstructure-evidence/device-seal/v1");j.put("algorithm","SHA256withECDSA/P-256");j.put("key_storage","ANDROID_KEYSTORE_NON_EXPORTABLE_PRIVATE_KEY");j.put("public_key_x509_b64",pub);j.put("public_key_sha256",Hashing.sha256(kp.getPublic().getEncoded()));j.put("inventory_sha256",invSha);j.put("inventory_canonicalization","UTF8 lines sorted by path: sha256\\tbytes\\tpath\\n");j.put("files",files);j.put("signature_der_b64",Base64.getEncoder().encodeToString(signed));j.put("external_anchor_status","NOT_EXTERNALLY_ANCHORED");j.put("sealed_wall_ms",System.currentTimeMillis());
        EvidenceWriter.atomicWrite(new File(dir,"seal.json"),j.toString(2));
        Verification v=verify(dir);if(!v.ok())throw new SecurityException("self-verification failed: "+v.reason());return j;
    }

    public static Verification verify(File dir)throws Exception{
        File sf=new File(dir,"seal.json");if(!sf.isFile())return new Verification(false,"SEAL_MISSING","");
        JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(sf.toPath()),StandardCharsets.UTF_8));JSONArray declared=j.getJSONArray("files");
        JSONArray actual=new JSONArray();String canonical=inventory(dir,actual,declared);String invSha=Hashing.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        if(!invSha.equals(j.optString("inventory_sha256")))return new Verification(false,"INVENTORY_HASH_MISMATCH",invSha);
        PublicKey pub=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(j.getString("public_key_x509_b64"))));
        java.security.Signature s=java.security.Signature.getInstance("SHA256withECDSA");s.initVerify(pub);s.update(canonical.getBytes(StandardCharsets.UTF_8));
        if(!s.verify(Base64.getDecoder().decode(j.getString("signature_der_b64"))))return new Verification(false,"SIGNATURE_INVALID",invSha);
        return new Verification(true,"PASS",invSha);
    }

    private static String inventory(File dir,JSONArray out,JSONArray declared)throws Exception{
        TreeMap<String,File> m=new TreeMap<>();collect(dir,dir,m);
        // seal.json is necessarily excluded from the inventory it describes.
        m.remove("seal.json");
        if(declared!=null){
            TreeSet<String> expected=new TreeSet<>();for(int i=0;i<declared.length();i++)expected.add(declared.getJSONObject(i).getString("path"));
            if(!m.keySet().equals(expected))throw new SecurityException("inventory path set mismatch");
        }
        StringBuilder c=new StringBuilder();
        for(var e:m.entrySet()){
            String path=e.getKey();File f=e.getValue();String sha=Hashing.sha256(f);long n=f.length();
            if(declared!=null){JSONObject d=find(declared,path);if(d==null||d.getLong("bytes")!=n||!sha.equals(d.getString("sha256")))throw new SecurityException("file mismatch: "+path);}
            JSONObject x=new JSONObject();x.put("path",path);x.put("bytes",n);x.put("sha256",sha);out.put(x);c.append(sha).append('\t').append(n).append('\t').append(path).append('\n');
        }
        return c.toString();
    }
    private static JSONObject find(JSONArray a,String path)throws Exception{for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);if(path.equals(x.getString("path")))return x;}return null;}
    private static void collect(File root,File f,Map<String,File> out)throws Exception{
        if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)collect(root,k,out);return;}
        String rel=root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/');
        if(rel.isBlank()||rel.contains("..")||rel.endsWith(".tmp"))return;out.put(rel,f);
    }
    private static KeyPair keyPair()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return new KeyPair(ks.getCertificate(ALIAS).getPublicKey(),(PrivateKey)ks.getKey(ALIAS,null));
        KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
        KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build();g.initialize(spec);return g.generateKeyPair();
    }
}
