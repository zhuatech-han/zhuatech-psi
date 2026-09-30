// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;

/** 提交幂等记录，与库存和资金过账处于同一事务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "mutation_stamp")
public class MutationStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "resource", nullable = false, length = 80)
  public String resource;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;
}
