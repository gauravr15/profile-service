package com.odin.profileservice.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import com.odin.profileservice.dto.StatusReadinessResponse;
import com.odin.profileservice.entity.*;
import com.odin.profileservice.enums.*;
import com.odin.profileservice.repo.*;
import com.odin.profileservice.utility.*;

class StatusReadinessServiceTest {
    ProfileRepository profiles=mock(ProfileRepository.class); UserRepository users=mock(UserRepository.class);
    PrivacySettingsRepository privacy=mock(PrivacySettingsRepository.class); SyncAuditRepository audits=mock(SyncAuditRepository.class);
    PhoneNumberHasher hasher=mock(PhoneNumberHasher.class); StatusReadinessService service;
    @BeforeEach void setUp(){ service=new StatusReadinessService(profiles,users,privacy,audits,hasher,new AccountStateValidator());
        when(profiles.findByCustomerId(1)).thenReturn(Profile.builder().customerId(1).mobile("919999999999").isActive(true).isDeleted(false).firstName("A").build());
        when(hasher.getCurrentPepperVersion()).thenReturn(1); when(hasher.hashWithGlobalPepper(anyString())).thenReturn("g".repeat(44));
        when(hasher.generateSalt()).thenReturn("salt"); when(hasher.hashPhoneNumber(anyString(),anyString())).thenReturn("p".repeat(64)); }
    @Test void distinguishesNeverSyncedFromSuccessfullySyncedEmpty(){ readyIdentity(); when(privacy.existsByUserId("1")).thenReturn(true);
        assertEquals(StatusReadinessState.REPAIR_REQUIRED,service.assess("1").getState());
        when(audits.existsById("1")).thenReturn(true); assertEquals(StatusReadinessState.READY,service.assess("1").getState()); }
    @Test void missingIdentityAndPrivacyAreExplicitlyRepairable(){
        assertEquals(StatusReadinessReason.MIDDLEWARE_USER_MISSING,service.assess("1").getReasons().get(0));
        readyIdentity(); when(audits.existsById("1")).thenReturn(true);
        assertEquals(StatusReadinessReason.PRIVACY_STATE_MISSING,service.assess("1").getReasons().get(0)); }
    @Test void repairCreatesIdentityAndPrivacyButStillRequiresExplicitContactSync(){
        when(users.findByGlobalPhoneHash(anyString())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(i->i.getArgument(0)); when(privacy.save(any())).thenAnswer(i->i.getArgument(0));
        when(users.findById("1")).thenReturn(Optional.empty()).thenReturn(Optional.of(user()));
        when(privacy.existsByUserId("1")).thenReturn(false).thenReturn(true);
        StatusReadinessResponse result=service.repair("1");
        assertEquals(StatusReadinessReason.CONTACT_SYNC_REQUIRED,result.getReasons().get(0));
        assertEquals("SYNC_CONTACTS",result.getRepairActions().get(0)); verify(users).save(any()); verify(privacy).save(any()); }
    @Test void inactiveAndConflictingOrInvalidIdentityNeverAllow(){
        when(profiles.findByCustomerId(1)).thenReturn(Profile.builder().customerId(1).isActive(false).isDeleted(false).build());
        assertNotEquals(StatusReadinessState.READY,service.assess("1").getState());
        when(profiles.findByCustomerId(1)).thenReturn(Profile.builder().customerId(1).mobile("919999999999").isActive(true).isDeleted(false).build());
        User bad=user(); bad.setGlobalPhoneHash("bad"); when(users.findById("1")).thenReturn(Optional.of(bad));
        assertEquals(StatusReadinessReason.GLOBAL_HASH_INVALID,service.assess("1").getReasons().get(0)); }
    private void readyIdentity(){ when(users.findById("1")).thenReturn(Optional.of(user())); }
    private User user(){ return User.builder().userId("1").globalPhoneHash("g".repeat(44)).pepperVersion(1).phoneHash("p".repeat(64)).phoneSalt("salt").build(); }
}
