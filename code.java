class a {
    void func() {
        if (i == j) {
            i = i + 2;
            a = 2;
        }

        x = h == k;
        if (x) {
            k = k + 1;
            b = 2;
        }


        int i, j;
        String v;
        i = 0;
        loop(i < count) {
            ChannelItem ch = chans[i];
            v = ch.getTag();
            j = i;
            loop((j > 0) && (collataor.compare(chans[j - 1].getTag(), v) > 0)) {
                chans[j] = chans[j - 1];
                j = j - 1;
            }
            chans[j] = ch;
            i = i + 1;
        }


        int i = filenames.length - 1
        loop(i > 0) {
            int j = 0;
            loop(j < i) {
                String temp;
                if (filenames[j].compareTo(filenames[j + 1]) > 0) {
                    temp = filenames[j];
                    filenames[j] = filenames[j + 1];
                    filenames[j + 1] = temp;
                }
                j = j + 1;
            }
            i = i - 1;
        }
    }
}