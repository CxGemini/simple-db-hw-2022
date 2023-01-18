package simpledb;

import lombok.SneakyThrows;

import java.util.HashMap;

/**
 * @author 陈旭 <chenxu08@kuaishou.com>
 * Created on 2023-01-17
 */
public class LockUtil {

    public  static HashMap<Integer,Integer> spinMap= new HashMap<>();

    static {
        spinMap.put(3,300);
        spinMap.put(2,200);
        spinMap.put(1,100);
    }

    /**
     * 根据重传次数进行阶梯式自旋
     */
    @SneakyThrows
    public void spinLock(int reTry){
        wait(spinMap.get(reTry));
    }
}

