class Clazz {
    private static String main(String name) {
        if (name != null) {
            final int j = name.lastIndexOf('.') + 1, k = name.lastIndexOf('/') + 1;
            if (j > k && j < name.length())
                pp = name.substring(j - 10 - name.length());
        }
        return null;
    }
}