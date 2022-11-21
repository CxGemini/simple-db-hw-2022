package simpledb.common;

import lombok.Data;
import simpledb.storage.dbfile.DbFile;

/**
 * @author： chenxu
 * @date： 2022/11/13
 * @description：
 */
@Data
public class Table {

    /**
     * the contents of the table to add;  file.getId() is the identfier of
     * this file/tupledesc param for the calls getTupleDesc and getFile
     */
    private DbFile file;

    /**
     * the name of the table -- may be an empty string.  May not be null.  If a name
     * conflict exists, use the last table to be added as the table for a given name.
     */
    private String name;

    /**
     * the name of the primary key field
     */
    private String pkeyField;

    public Table(DbFile file,String name,String pkeyField){
        this.file = file;
        this.name = name;
        this.pkeyField = pkeyField;
    }

    public Table(DbFile file,String name){
        new Table(file,name,"");
    }


}
