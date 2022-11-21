package simpledb;

/**
 * @author： chenxu
 * @date： 2022/11/14
 * @description：
 */
public class LogUtils {
    public static void print(Object ...objs){
        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = true;
        for (Object obj : objs) {
            if(flag){
                stringBuilder.append((obj)).append(":");
                flag = false;
            }else {
                stringBuilder.append(obj).append("; ");
                flag = true;
            }

        }
        System.out.println(stringBuilder);
    }
}
