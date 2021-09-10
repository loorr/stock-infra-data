package org.example.snapshot;

import lombok.Data;

@Data
public class StockSnapshot {
    /** ���� */
    private String arrow;

    private Double ask1;
    private Double ask2;
    private Double ask3;
    private Double ask4;
    private Double ask5;
    private Integer askvol1;
    private Integer askvol2;
    private Integer askvol3;
    private Integer askvol4;
    private Integer askvol5;
    private Double bid2;
    private Double bid3;
    private Double bid4;
    private Double bid5;
    private Integer bidvol1;
    private Integer bidvol2;
    private Integer bidvol3;
    private Integer bidvol4;
    private Integer bidvol5;
    private String code;

    private Double high;
    private Double low;
    /** ֤ȯ���� */
    private String name;
    private Double open;
    /** �ǵ��� */
    private Double percent;
    /** ��ǰ�۸� */
    private Double price;
    /** û�� */
    private Integer status;
    private String symbol;
    private String time;
    /** �ɽ��� */
    private Long turnover;
    /** �г����� SZ SH */
    private String type;
    private String update;
    /** */
    private Double updown;
    private Long volume;
    private Double yestclose;
}
