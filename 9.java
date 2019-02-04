class Clazz {
    String main(int start, int stop) {
        StringBuilder builder = new StringBuilder();
        int i = start;
        while (i <= stop) {
            if (i > start) builder.append(',');
            builder.append(i);
            i = i + 1;
        }
        output = builder.toString();
    }
}