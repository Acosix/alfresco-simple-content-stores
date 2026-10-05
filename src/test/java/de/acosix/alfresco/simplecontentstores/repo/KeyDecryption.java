package de.acosix.alfresco.simplecontentstores.repo;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.alfresco.util.ParameterCheck;
import org.apache.commons.codec.binary.Hex;

public class KeyDecryption
{

    private static final String[] KEYS = {
            "0a798ebaa000f70145a23661ca832ea5cd76ba4540dbc16c568f38240e11e39dfeaf4bddd807c6d4a25d42caf747fba9607526024a696e23bea3dd94effe5a8dbae88ee82b9b9c03886e7c52353eb33579de9c86f93cf8b5c9bca94b6f7d5f7f7e45af42fbd863699ac7559e724e8cb380a624d2b7058f3df6dc8e2c317aa81f3cd67ad512a5ad334f5528cb97f14f50676b6ba878e0fbae861bb1e36a5936071d0d6618d4e3ae5943491c35ccf5f29b39ed9d169f6708acb930dfb0a17e5c8bca289f1c7c2450487854f20771adb2c3f56fcf57763db9e5a605aaa2ce493bebb81c8230cff074f950e907c30a5fedf535591130eca98c5998422ec191bf10b64e0e7dc62d7668e73b1f249b955293324833a6f5f6b22ec012b944972dcad0633857f2213f16f5cfe97b9c2be2ef976a73900e9a4e99f26aa24cd7aa6d1fd4d4a2d80369c97a61893f2409836efe13835fe58d102aee9e089804dd860a2ae4e128d9e574050cccb0cda77f16e09d54f2447b430382dc3a7073b0f3bbe02367243ab9b4388c48327282e21b5b7510b9b69895d9d2ab8d0090aebd868bea88b0dbf63becee0ef19d2195e536dce5096e5fa65d1a3789e1f19ae8351b013e88409b8668f29320687d08aebf4a03cce1003a1e783ebc74ea8397e65674938fb18084f590910210c7710e13c9d6c78a0e443fca6848a5c221ea45fb56b2b33289042d",
            "431897765433fa6dac829a27fea3882626d25eb0b74aab597ab70753c95492bf3e6837103aea6c9e752e4612045351a67f11e57247ea0a79f1db5e36fb65199e0d98b3c48492f753b750cb60e73fb9315d5978612eb035194dcbf5e4eb96a16aa50145e407766e3d83b7bcf1c6e80dcf17ddffe55c84a43fd2c7b6a4f6c2b2c5f7e8c9c611790e67f6d56399d3493dac63ac839ba0763e20defd3b2188a9600a4e4b8a44fc8adc512ca538ce642bd2f66a09cc532efaa7a344c22ac77541ba7c7191100d2194be2d2f4c94a7edc9cb227b29107672601f7d49654c5c5fd7cb9926b17daa99cde4f03a8bc881fbe0710a62a32a7312b103656d105f3b41d71d3297432dd1cd823cf90a2c0ae0d8261964733c81085e91b81a33f70377b8b76d9000b4a3c321e7b4fd09bff1916895feec375738cdb2544ea9b3600b0ad3e45f67622b37f15d7999dac35b6e53b88a3c085cf36c5462540914de59b7ff145bd7de9473a22429f88d937c97793d376c5ad0b56c474afd1c6e35b001b9f590ad70a3488c7e21467b7981335d405dde027a03ef7dca26677c978ee8b0b2bdcd101205b736bbc246d32090e2c0b674ed36bfd450dcbd053a4686c89843fa2525c770f423e4ed2c30a1b48891b6592ae1e00858e0d8ec6e1b33152535e8dd8922d0652b6dd8fc4ded59949d7b3fc4f87c4fe430eef0cb04f19563526df89f7d75c9e925",
            "38cd23cbb0430adf38d308c0ccbe4ed1063431ff8999e528f4b0577fc94b13ed854ac75b008b4c664d965fb3fc7dcb0acf6efb71d0322ced4427d27a27b3c7ab4ead009fbfa39c896b3a3714eb6b7f7288888b9a344f239b6ca19ec0eed684b591dd6c5dec108132373ad08b675e3dfeeae069c1d4987541ea4854088768c2a7edc565f69285513c07ac9464b66a16ec882ed1f049fdc753dc79051936e1f77ae7fc1b7fec125695a6f1feb7d88972a300ce3e3287626548a49737b3e0800705823f8fe902df0e9efddde6e4558973b84e46e1994b21f7b35fbb6795e7325d649514097b3c2345b00aeeb957a51b05eff2352d8d647b6287f03c8c0ab9512dee0d6f26c2a09fa9229d710f90cb29e99f13bca6ce3b036a5e3c9681325001f646b9063dbbe006456d624a40fd409528acb40f56f4eba8f973e5e751e4751279d6088285bb3dc8b7b886d4f282147ddbf9d6a169ce56f47a89f68b6e31e1efbd65d6ddcef7887952b935f721c58d10c5af9515957d2a65256f72e9c272cd94b8da27f73474e3da99d65236d652414e77f29f55ebded8a6e9ecf41ba04790d51f40f5a9915545c0699c5a43911b7aabcf4f6dc448b47514b1448e7fe2b0fddb8c871a0f21fc50de844ce2ff4f75c9699da8d5cd1f7b7d6ef5970eb78c51a1736c042f9cbf53e0208f46959d53ee11222818d0c0de209e89a09ad138f35b37707d2c",
            "898235fe886e83d6a10c6ca243b7fd74cded40af2c3751ef3cbbc6aa5c02e61238856500c6babfed275ab8958297c24d3527e15aa6830b2302dfd757c38ecd81bfc4744cd4aeb90e6cd1b90fa705c295618cc80dfc551c5699746f129ebc14e28ee4b37cee03fc4da701765e2ed9f9b66b84b8a4838e2a83afbdd70df0fad05b76114e58d57b17461b510eb6f5270c729c97640cc936a9c67f1349db6689454d2ad72e010d0c3aac6386330920e6fd7d3e334a53e3969fe4acb515cc3052d9bc702c0702e8e7c7adfe0f82949ebf11cdd00b5fb2037a9f3315f57031107c252ecc682254395796b466505e50f641856a374b6cdc9ae2da14e3db18a9db15e81a1fac3eac5476e866b631353ece8de40d8c84c9c8075d5c872162034e6b1286400bc80a297c721dd1f9e073a8b5045f7a98948e08ef8d9dd98ae1421624262d1ac8fbdd3b7900c51ede0627d40a78fddab38b8d02e71ec606ee2ac47dc11020c70c38f573f6eecb744d1469a7b7678ebd3c58ac66126cc74a1fbe8b3d635773fa62da833b3a7b57f1045d1393743113dcba62b310a2bb390df4b512059fc9f09aef6f3871631108b395736abe6791581aa364b19c5ae39ed6213c92cb8ac54721ca7f02ecee03481d9ac72aed7bbbe1875aa4bd8058d1fd4d177975d2fc3ed61e373dbce445ace63764e66446c0a12ab131b3ec8ab57c68032b784a3a4473cd5c",
            "122c4533611deb2cfc59eebe2902d185f78d6adb5f15518ecb6b1b2e79b9ee778729e08a38110b573480869df2e129ed3a48d6e413477f31fd0430915c3f6a19db10fb40a4725c759c69291d4a179aa01ed85c9b2bc95d0a7e0fde7d1a7ccde804f6b38bb1f48977f703b74ab1c401edab2eddcdaa864738881990130d5ae82e4ac29d7c542c6c2a3265ddfc2a5f3e2cdf1372b7aec52a0e69ed5010060ae5af38da19f1255b2e2ce033f43c8b6a1da4b619c55e9760b0de626f525a81e227e7683ae4358a843198779e853bd61c2a0cf5f0c470a316262bcd330275aadb822ce3892146f3fe80b78b62b4740e85e56a20cd44cc06d2672bd7c1a0e38d3a511e211832ec4a416b1a176e9cbc8a033867fa988b507bac03d37f452be925358c4306b4d39c01447a1293c18a502723cbdb670d6f0d9e510b1f6f141dc2b07d54100560da053800d2b5695518ca906f44e8504c9c6b11cfa797acd0b90c2f84b52a06e1376675bc9a1b00fcd5c0169c83fc4daf26553d7aad0212bf1bc21ac88dd73903fbbf3ed9f402a7d5d979b4c493e5546ee10dab43dc85a8373336545d2028fc18f5665847bf0d58af1f03db7086cf4d4fce4f5a147f570796a7fd2dda283786e0f90b86f5534869d21b62806907d0ec67fd2d1296b51766e95060f48d54c74849ad4f30a7022e6683b8c1d2957d4524c424b60ea8151a81483e7904813645"
    };
    
    private static final String[] OTHER_KEYS = {
            "b425a671f8506fa301c15c0adc1ff8762b70d56a49f0c167dd0f435ba0e68467ab27c88fd29b14ab1fecec319c67acecddc00eaf64a4a061213aef6776c3f279190da9a2d55aecba4423122c09d4a8b94840ee7846050b36c11b6173673e1a57b0fbc9db81a74f27a5a790a0d0012f0b33baa01586b8d88a3eb36638fd0540e2c59c8d89efc34dc6c180fd7fb2150763eaf9402e54362a58a71fdb8ea750987fb9fa25567ed3a40aaf099161fcba333e0e1944080b4ac5d9262c3f9e3b9a778b6bf65643e8246d298d9cd51f1dec8e1bb64d04743e07f1daf4987ea8aed42a14862cb7c31a6d627fb807820420baf30968019db67dd4f8effb000e281a2a5638",
            "81fc54c996c1a876acb53fa3e47aba692a27df7ee7e11ba93a388191c68c0ff3954c684e718dc17991ea6d5c520a8ffa0d002c5c388b1cb09eacee36022ea5cf2e33445a11884ebb6713873be54dd4d405624328b26aba65aac142579dacaf728aa6b27dcd1cac14923b5adce82f9e5336fb79f47fb18f93ce61a5125451df26b22053a1db13d1ad4e6b13a4cb1988009f877ddf8a3af1cfcbfd65512b48858e554fcb7848dc3e915497a20deab31897560849184122d710b62ba14f72d1b4aeba6e42ac34838bcef247a7423dc5e9712a90f2b4daa655cdd5b07f822626a2ce299cf69b9145465ebbecb47823ce60a31442b4b14a2c11c432473d09e83d2ac3"
    };

    private static final String[] RESOURCES = { "3d9ea5f1-807a-486a-810a-81c871adc52f.bin", "fe01c226-b391-4ec0-943f-ee290bb08e4e.bin",
            "32753a7a-ef36-4cb8-a4a5-c7f9f180f51e.bin", "bd78632e-80e2-42ed-8e05-e2f6b69a89b9.bin",
            "bc49e9ad-fcfc-403e-ba3e-2280d602a53e.bin" };

    private static final String[] OTHER_RESOURCES = { "ba50f10d-4df4-4ba0-9161-3038be5fab80.bin", "05efb43b-2808-4a1f-b25a-83f1c24f8bcf.bin" };

    private static final String[] OTHER_ALGS = { "DES", "DESede" };

    private static final Map<String, String> PADDINGS_BY_ALGORITHM;
    static
    {
        // paddings required to be supported by Java Security specs
        final Map<String, String> paddings = new HashMap<>();

        paddings.put("AES", "CBC/PKCS5Padding");
        paddings.put("DES", "CBC/PKCS5Padding");
        paddings.put("DESede", "CBC/PKCS5Padding");

        PADDINGS_BY_ALGORITHM = Collections.unmodifiableMap(paddings);
    }

    public static void main(String[] args) throws Exception
    {
        handleMainKeys();
        handleOtherKeys();
    }
    
    private static void handleMainKeys() throws Exception
    {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = KeyDecryption.class.getResourceAsStream("/encryption.keystore"))
        {
            ks.load(is, "dockerTest".toCharArray());
        }
        Key key = ks.getKey("effs", "dockerTest".toCharArray());
        Cipher cipher = getInitialisedCipher(key, false);

        for (int i = 0; i < KEYS.length; i++)
        {
            String symKey = KEYS[i];
            byte[] bytes = Hex.decodeHex(symKey);

            final ByteBuffer sourceBuffer = ByteBuffer.wrap(bytes);
            final int targetBufferSize = cipher.getOutputSize(sourceBuffer.remaining());
            final ByteBuffer targetBuffer = ByteBuffer.allocateDirect(targetBufferSize);
            cipher.doFinal(sourceBuffer, targetBuffer);
            targetBuffer.flip();

            final byte[] keyBytes = new byte[targetBuffer.remaining()];
            targetBuffer.get(keyBytes);
            String decryptedHex = Hex.encodeHexString(keyBytes);
            System.out.println(decryptedHex);

            /*
            Key symKeyObj = new SecretKeySpec(keyBytes, "AES");
            Cipher cipherRes = getInitialisedCipher(symKeyObj, false);
            String resource = "/" + RESOURCES[i];
            Files.createDirectories(Paths.get("decoded"));
            try (InputStream is = KeyDecryption.class.getResourceAsStream(resource))
            {
                try (OutputStream os = Files.newOutputStream(Paths.get("decoded", RESOURCES[i]), StandardOpenOption.CREATE_NEW))
                {
                    byte[] readBuf = new byte[1024];
                    byte[] writeBuf = new byte[1024];
                    int bytesRead = 0;
                    int bytesWritten = 0;

                    while ((bytesRead = is.read(readBuf)) != -1)
                    {
                        bytesWritten = cipherRes.update(readBuf, 0, bytesRead, writeBuf);
                        if (bytesWritten > 0)
                        {
                            os.write(writeBuf, 0, bytesWritten);
                        }
                    }

                    bytesWritten = cipherRes.doFinal(readBuf, 0, 0, writeBuf);
                    if (bytesWritten > 0)
                    {
                        os.write(writeBuf, 0, bytesWritten);
                    }
                }
            }
            */
        }
    }

    private static void handleOtherKeys() throws Exception
    {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = KeyDecryption.class.getResourceAsStream("/keystore.jks"))
        {
            ks.load(is, "password".toCharArray());
        }
        Key key = ks.getKey("firstkey", "password".toCharArray());
        Cipher cipher = getInitialisedCipher(key, false);

        for (int i = 0; i < OTHER_KEYS.length; i++)
        {
            String symKey = OTHER_KEYS[i];
            byte[] bytes = Hex.decodeHex(symKey);

            final ByteBuffer sourceBuffer = ByteBuffer.wrap(bytes);
            final int targetBufferSize = cipher.getOutputSize(sourceBuffer.remaining());
            final ByteBuffer targetBuffer = ByteBuffer.allocateDirect(targetBufferSize);
            cipher.doFinal(sourceBuffer, targetBuffer);
            targetBuffer.flip();

            final byte[] keyBytes = new byte[targetBuffer.remaining()];
            targetBuffer.get(keyBytes);
            String decryptedHex = Hex.encodeHexString(keyBytes);
            System.out.println(decryptedHex);

            Key symKeyObj = new SecretKeySpec(keyBytes, OTHER_ALGS[i]);
            Cipher cipherRes = getInitialisedCipher(symKeyObj, false);
            String resource = "/" + OTHER_RESOURCES[i];
            Files.createDirectories(Paths.get("decoded"));
            try (InputStream is = KeyDecryption.class.getResourceAsStream(resource))
            {
                try (OutputStream os = Files.newOutputStream(Paths.get("decoded", OTHER_RESOURCES[i]), StandardOpenOption.CREATE_NEW))
                {
                    byte[] readBuf = new byte[1024];
                    byte[] writeBuf = new byte[1024];
                    int bytesRead = 0;
                    int bytesWritten = 0;

                    while ((bytesRead = is.read(readBuf)) != -1)
                    {
                        bytesWritten = cipherRes.update(readBuf, 0, bytesRead, writeBuf);
                        if (bytesWritten > 0)
                        {
                            os.write(writeBuf, 0, bytesWritten);
                        }
                    }

                    bytesWritten = cipherRes.doFinal(readBuf, 0, 0, writeBuf);
                    if (bytesWritten > 0)
                    {
                        os.write(writeBuf, 0, bytesWritten);
                    }
                }
            }
        }
    }

    protected static Cipher getInitialisedCipher(final Key key, final boolean encrypt) throws GeneralSecurityException
    {
        ParameterCheck.mandatory("key", key);

        String algorithm = key.getAlgorithm();
        Cipher cipher = Cipher.getInstance(algorithm);
        if (cipher.getBlockSize() == 0)
        {
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
        }
        else
        {
            if (PADDINGS_BY_ALGORITHM.containsKey(algorithm))
            {
                algorithm = algorithm + "/" + PADDINGS_BY_ALGORITHM.get(algorithm);
            }
            cipher = Cipher.getInstance(algorithm);
            // no way to record/transport iv for each key in Alfresco (also symmetric keys are only used for one encryption)
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[cipher.getBlockSize()]));
        }
        return cipher;
    }
}
