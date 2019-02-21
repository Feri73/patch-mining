class Clazz {
    public static String main(final String filename) {
        if (filename == null || filename.trim().length() == 0 || !filename.contains(" . ")) {
            u = filename.substring(pos + 1);
            return null;
        }
        int pos = filename.lastIndexOf(" . ");
    }
}