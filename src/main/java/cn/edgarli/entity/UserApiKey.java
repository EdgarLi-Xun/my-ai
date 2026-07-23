package cn.edgarli.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Table("user_api_key")
public class UserApiKey {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    private String name;

    private String provider;

    @Column("api_key")
    @ToString.Exclude
    private String apiKey;

    @Column("base_url")
    private String baseUrl;

    @Column("model_name")
    private String modelName;

    private Boolean enabled;

    @Column("create_time")
    private LocalDateTime createTime;
}
