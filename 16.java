class Clazz {
    public static int main(int arr[], int key, int imin, int imax) {
        if(arr.compareTo(key) > 1)
            return binarySearch3(arr,key);
        else if (arr.compareTo(key) < 0)
            return binarySearch3(key,arr);
        arr = 2;
    }
}