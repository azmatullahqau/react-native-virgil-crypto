
package com.virgilsecurity.rn.crypto;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;

import com.virgilsecurity.rn.crypto.utils.FS;
import com.virgilsecurity.rn.crypto.utils.InvalidOutputFilePathException;
import com.virgilsecurity.sdk.crypto.HashAlgorithm;
import com.virgilsecurity.sdk.crypto.KeyType;
import com.virgilsecurity.sdk.crypto.VirgilCrypto;
import com.virgilsecurity.sdk.crypto.VirgilKeyPair;
import com.virgilsecurity.sdk.crypto.VirgilPrivateKey;
import com.virgilsecurity.sdk.crypto.VirgilPublicKey;
import com.virgilsecurity.sdk.crypto.exceptions.CryptoException;

import com.virgilsecurity.rn.crypto.utils.Encodings;
import com.virgilsecurity.rn.crypto.utils.ResponseFactory;
import com.virgilsecurity.sdk.crypto.exceptions.DecryptionException;
import com.virgilsecurity.sdk.crypto.exceptions.EncryptionException;


public class RNVirgilCryptoModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private final VirgilCrypto crypto;

  public static ReactApplicationContext RCTContext;
  private static LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
  private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
          2,
          8,
          5000,
          TimeUnit.MILLISECONDS,
          taskQueue);

  public RNVirgilCryptoModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.crypto = new VirgilCrypto();

    RCTContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNVirgilCrypto";
  }

  @Override
  public Map<String, Object> getConstants() {
    Map<String, String> keyPairTypeMap = new HashMap<>();
    keyPairTypeMap.put("CURVE25519", KeyType.CURVE25519.name());
    keyPairTypeMap.put("ED25519", KeyType.ED25519.name());
    keyPairTypeMap.put("SECP256R1", KeyType.SECP256R1.name());
    keyPairTypeMap.put("RSA2048", KeyType.RSA_2048.name());
    keyPairTypeMap.put("RSA4096", KeyType.RSA_4096.name());
    keyPairTypeMap.put("RSA8192", KeyType.RSA_8192.name());

    Map<String, String> hashAlgMap = new HashMap<>();
    hashAlgMap.put("SHA224", HashAlgorithm.SHA224.name());
    hashAlgMap.put("SHA256", HashAlgorithm.SHA256.name());
    hashAlgMap.put("SHA384", HashAlgorithm.SHA384.name());
    hashAlgMap.put("SHA512", HashAlgorithm.SHA512.name());

    Map<String, Object> constantsMap = new HashMap<>();
    constantsMap.put("KeyPairType", keyPairTypeMap);
    constantsMap.put("HashAlgorithm", hashAlgMap);
    return constantsMap;
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap computeHash(String dataBase64) {
    byte[] hash = this.crypto.computeHash(Encodings.decodeBase64(dataBase64));
    return ResponseFactory.createStringResponse(Encodings.encodeBase64(hash));
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap computeHashWithAlgorithm(String dataBase64, String algorithm) {
    byte[] hash = this.crypto.computeHash(Encodings.decodeBase64(dataBase64), HashAlgorithm.valueOf(algorithm));
    return ResponseFactory.createStringResponse(Encodings.encodeBase64(hash));
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateKeyPair() {
    try {
      VirgilKeyPair keypair = this.crypto.generateKeyPair();
      return ResponseFactory.createMapResponse(this.exportAndEncodeKeyPair(keypair));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateKeyPairOfType(String type) {
    try {
      VirgilKeyPair keypair = this.crypto.generateKeyPair(KeyType.valueOf(type));
      return ResponseFactory.createMapResponse(this.exportAndEncodeKeyPair(keypair));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateKeyPairUsingSeed(String seedBase64) {
    try {
      VirgilKeyPair keypair = this.crypto.generateKeyPair(Encodings.decodeBase64(seedBase64));
      return ResponseFactory.createMapResponse(this.exportAndEncodeKeyPair(keypair));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateKeyPairWithTypeAndSeed(String type, String seedBase64) {
    try {
      Log.d("generate_keys", "with type and seed" + KeyType.valueOf(type).name());
      VirgilKeyPair keypair = this.crypto.generateKeyPair(KeyType.valueOf(type), Encodings.decodeBase64(seedBase64));
      return ResponseFactory.createMapResponse(this.exportAndEncodeKeyPair(keypair));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap encrypt(String dataBase64, ReadableArray recipientsBase64) {
    try {
      List<VirgilPublicKey> publicKeys = this.decodeAndImportPublicKeys(recipientsBase64);
      byte[] encryptedData = this.crypto.encrypt(Encodings.decodeBase64(dataBase64), publicKeys);
      return ResponseFactory.createStringResponse(Encodings.encodeBase64(encryptedData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap decrypt(String dataBase64, String privateKeyBase64) {
    try {
      VirgilKeyPair keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
      byte[] decryptedData = this.crypto.decrypt(Encodings.decodeBase64(dataBase64), keypair.getPrivateKey());

      return ResponseFactory.createStringResponse(Encodings.encodeBase64(decryptedData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateSignature(String dataBase64, String privateKeyBase64) {
    try {
      VirgilKeyPair keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
      byte[] signatureData = this.crypto.generateSignature(Encodings.decodeBase64(dataBase64), keypair.getPrivateKey());
      return ResponseFactory.createStringResponse(Encodings.encodeBase64(signatureData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap verifySignature(String signatureBase64, String dataBase64, String publicKeyBase64) {
    try {
      VirgilPublicKey publicKey = this.crypto.importPublicKey(Encodings.decodeBase64(publicKeyBase64));
      boolean isValid = this.crypto.verifySignature(
              Encodings.decodeBase64(signatureBase64),
              Encodings.decodeBase64(dataBase64),
              publicKey
      );
      return ResponseFactory.createBooleanResponse(isValid);
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap signAndEncrypt(String dataBase64, String privateKeyBase64, ReadableArray recipientsBase64)
  {
    try {
      VirgilKeyPair keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
      List<VirgilPublicKey> publicKeys = this.decodeAndImportPublicKeys(recipientsBase64);
      byte[] encryptedData = this.crypto.signThenEncrypt(Encodings.decodeBase64(dataBase64), keypair.getPrivateKey(), publicKeys);
      return ResponseFactory.createStringResponse(Encodings.encodeBase64(encryptedData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap decryptAndVerify(String dataBase64, String privateKeyBase64, ReadableArray sendersPublicKeysBase64)
  {
    try {
      VirgilKeyPair keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
      List<VirgilPublicKey> publicKeys = this.decodeAndImportPublicKeys(sendersPublicKeysBase64);
      byte[] decryptedData = this.crypto.decryptThenVerify(Encodings.decodeBase64(dataBase64), keypair.getPrivateKey(), publicKeys);
      return ResponseFactory.createStringResponse(Encodings.encodeBase64(decryptedData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap extractPublicKey(String privateKeyBase64) {
    try {
      VirgilKeyPair keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
      byte[] publicKeyData = this.crypto.exportPublicKey(keypair.getPublicKey());
      return ResponseFactory.createStringResponse(Encodings.encodeBase64(publicKeyData));
    }
    catch (CryptoException e) {
      return ResponseFactory.createErrorResponse(e);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap generateRandomData(Integer size) {
    byte[] randomData = this.crypto.generateRandomData(size);
    return ResponseFactory.createStringResponse(Encodings.encodeBase64(randomData));
  }

  @ReactMethod
  public void encryptFile(String inputUri, String outputUri, ReadableArray recipientsBase64, final Promise promise) {
    final List<VirgilPublicKey> publicKeys;
    try {
      publicKeys = this.decodeAndImportPublicKeys(recipientsBase64);
    }
    catch (CryptoException e) {
      promise.reject("invalid_public_key", "Public keys array contains invalid public keys");
      return;
    }

    final String inputPath = FS.normalizePath(inputUri);
    final String outputPath;
    if (outputUri == null) {
      outputPath = FS.getTempFilePath(FS.getFileExtension(inputPath));
    } else {
      outputPath = outputUri;
    }

    final VirgilCrypto vc = this.crypto;

    threadPool.execute(new Runnable() {
      @Override
      public void run() {
        try {
          InputStream inStream = FS.getInputStreamFromPath(inputPath);
          OutputStream outStream = FS.getOutputStreamFromPath(outputPath);
          vc.encrypt(inStream, outStream, publicKeys);
          outStream.close();
          inStream.close();
          promise.resolve(outputPath);
        } catch (FileNotFoundException e) {
          promise.reject(
                  "invalid_input_file",
                  String.format("File does not exist at path %s", inputPath)
          );
        } catch (InvalidOutputFilePathException e) {
          promise.reject("invalid_output_file", e.getLocalizedMessage());
        } catch (EncryptionException e) {
          promise.reject(
                  "failed_to_encrypt",
                  String.format("Could not encrypt file; %s", e.getLocalizedMessage())
          );
        } catch (IOException e) {
          promise.reject("unexpected_error", e.getLocalizedMessage());
        }
      }
    });
  }

  @ReactMethod
  public void decryptFile(String inputUri, String outputUri, String privateKeyBase64, final Promise promise) {
    VirgilKeyPair keypair;
    try {
      keypair = this.crypto.importPrivateKey(Encodings.decodeBase64(privateKeyBase64));
    } catch (CryptoException e) {
      promise.reject("invalid_private_key", "The given value is not a valid private key");
      return;
    }

    final String inputPath = FS.normalizePath(inputUri);
    final String outputPath;
    if (outputUri == null) {
      outputPath = FS.getTempFilePath(FS.getFileExtension(inputPath));
    } else {
      outputPath = outputUri;
    }

    final VirgilCrypto vc = this.crypto;
    final VirgilPrivateKey privateKey = keypair.getPrivateKey();

    threadPool.execute(new Runnable() {
      @Override
      public void run() {
        try {
          InputStream inStream = FS.getInputStreamFromPath(inputPath);
          OutputStream outStream = FS.getOutputStreamFromPath(outputPath);
          vc.decrypt(inStream, outStream, privateKey);
          outStream.close();
          inStream.close();
          promise.resolve(outputPath);
        } catch (FileNotFoundException e) {
          promise.reject(
                  "invalid_input_file",
                  String.format("File does not exist at path %s", inputPath)
          );
        } catch (InvalidOutputFilePathException e) {
          promise.reject("invalid_output_file", e.getLocalizedMessage());
        } catch (DecryptionException e) {
          promise.reject(
                  "failed_to_decrypt",
                  String.format("Could not decrypt file; %s", e.getLocalizedMessage())
          );
        } catch (IOException e) {
          promise.reject("unexpected_error", e.getLocalizedMessage());
        }
      }
    });
  }

  private List<VirgilPublicKey> decodeAndImportPublicKeys(ReadableArray publicKeysBase64) throws CryptoException {
    List<VirgilPublicKey> publicKeys = new ArrayList<>(publicKeysBase64.size());
    for(Object publicKeyBase64 : publicKeysBase64.toArrayList()) {
      publicKeys.add(this.crypto.importPublicKey(Encodings.decodeBase64((String)publicKeyBase64)));
    }
    return publicKeys;
  }

  private Map<String, String> exportAndEncodeKeyPair(VirgilKeyPair keypair) throws CryptoException {
    final byte[] privateKeyData = this.crypto.exportPrivateKey(keypair.getPrivateKey());
    final byte[] publicKeyData = this.crypto.exportPublicKey(keypair.getPublicKey());
    Map<String, String> keypairMap = new HashMap<>();
    keypairMap.put("privateKey", Encodings.encodeBase64(privateKeyData));
    keypairMap.put("publicKey", Encodings.encodeBase64(publicKeyData));
    return keypairMap;
  }
}