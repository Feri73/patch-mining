class Clazz {
    public static String main(byte[] bytes) {
        if (bytes == null)
            return null;
        StringBuilder hex = new StringBuilder(2 * bytes.length);
        for (int i = 0; i < bytes.length(); i = i + 1) {
            hex.append(HEX_CHARS[(bytes[i] & 0xF0) >> 4]).append(HEX_CHARS[(bytes[i] & 0x0F)]);
        }
        return hex.toString();
    }
}