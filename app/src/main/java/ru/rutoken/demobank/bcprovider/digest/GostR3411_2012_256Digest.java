package ru.rutoken.demobank.bcprovider.digest;

import static ru.rutoken.pkcs11jna.Pkcs11Tc26Constants.CKM_GOSTR3411_12_256;

import ru.rutoken.pkcs11jna.Pkcs11;

class GostR3411_2012_256Digest extends Pkcs11Digest {
    private static final byte[] ATTR_GOSTR3411_2012_256 =
            {0x06, 0x08, 0x2a, (byte) 0x85, 0x03, 0x07, 0x01, 0x01, 0x02, 0x02};

    GostR3411_2012_256Digest(Pkcs11 pkcs11, long sessionHandle) {
        super(pkcs11, sessionHandle, CKM_GOSTR3411_12_256, ATTR_GOSTR3411_2012_256);
    }

    @Override
    public Type getType() {
        return Type.GOSTR3411_2012_256;
    }

    @Override
    public String getAlgorithmName() {
        return "PKCS11-GOSTR3411-2012-256";
    }

    @Override
    public int getDigestSize() {
        return 32;
    }
}
