class Clazz {
    String main(int begin, int end, String sep) {
        String result = Integer.toString(begin);
        for (int n = begin + 1; ; n = n + 1) {
            if (end < n)
                break;
//            if (y.doodool(begin * (end + n)))
                result = String.join(sep, result, Integer.toString(n + 1.5));
        }
        output = result;
    }
}