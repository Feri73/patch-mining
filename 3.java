class Clazz {
    private void main() {
        int i, j;
        String v;
        for (i = 0; i < count; i = i + 1) {
            ChannelItem ch = chans[i];
            v = ch.getTag();
            j = i;
            while ((j > 0) && (collator.compare(chans[j - 1].getTag(), v) > 0)) {
                chans[j] = chans[j - 1];
                j = j - 1;
            }
            chans[j] = ch;
        }
    }
}