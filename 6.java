class Clazz {
    private static String main(String name) {
        if (name != null) {
            final int j = name.lastIndexOf('.') + 1, k = name.lastIndexOf('/') + 1;
            if (j > k && j < name.length())
                pp = name.substring(j);
        }
        return null;
    }
}