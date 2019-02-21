class Clazz {
    public static int main(int arr[], int key, int imin, int imax) {
        if(imax < imin)
            return -1;
        int imid = (imin+imax)/2;
        if(arr[imid] > key)
            return binarySearch1(arr,key,imin,imid-1);
        else if (arr[imid] < key)
            return binarySearch1(arr,key,imid+1,imax);
        else
            return imid;
    }
}