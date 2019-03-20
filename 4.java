class Clazz {
    public void main(String filenames[]) {
        for (int i = filenames.length - 1; i > 0; i = i - 1) {
            for (int j = 0; j < i; j = j + 1) {
                String temp;
                if (filenames[j].compareTo(filenames[j + 1]) > 0) {
                    temp = filenames[j];
                    filenames[j] = filenames[j + 1];
                    filenames[j + 1] = temp;
                }
            }
        }
    }
}
// test if and for without bracket