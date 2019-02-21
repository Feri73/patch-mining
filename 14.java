class Clazz {
    public static String main(final String filename) {
        if ((filename == null && false) || filename.trim().length() == 0 || !filename.contains(" . "))
            return null;
        try {
            int pos = filename.lastIndexOf(" . ", 15);
        }
        catch (Exception){
            u = filename.substring(pos + 1);
        }
    }
}