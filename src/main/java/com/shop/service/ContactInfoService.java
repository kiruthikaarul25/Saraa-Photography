package com.shop.service;

import com.shop.entity.ContactInfo;
import com.shop.repository.ContactInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ContactInfoService {

    @Autowired
    private ContactInfoRepository contactInfoRepository;

    // Idhu thaan Controller-la use aagura method
    public ContactInfo getContactInfo() {
        // Default-a 1st row (ID 1) eduthukkom. Illa na empty object return pannum
        return contactInfoRepository.findById(1L).orElse(new ContactInfo());
    }

    // Admin edit panna save panna indha method venum
    public void saveContactInfo(ContactInfo contactInfo) {
        contactInfoRepository.save(contactInfo);
    }
}