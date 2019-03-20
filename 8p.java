class Clazz {
    String main(int start, int stop) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= stop; i = i + 1) {
            if (i > start) builder.append(',');
            builder.append(i);
            builder.append(builder).append(',')
        }
        return builder.toString();
    }
}