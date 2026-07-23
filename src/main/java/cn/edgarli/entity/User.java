package cn.edgarli.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table("user")
public class User {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String name;

    private String email;

    @Column("default_key_id")
    private Long defaultKeyId;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("password_hash")
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;
}
